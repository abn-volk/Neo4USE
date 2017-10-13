package org.uet.neo4use.actions;

import java.awt.EventQueue;
import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.config.Options;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.tzi.use.uml.ocl.type.CollectionType;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.type.Type.VoidHandling;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.sys.MObject;

public class ActionImport implements IPluginActionDelegate {
	
	private MainWindow fMainWindow;
	private PrintWriter fLogWriter;
	private UseSystemApi fSystemApi;

	@Override
	public void performAction(IPluginAction pluginAction) {
		fMainWindow = pluginAction.getParent();
		fLogWriter = fMainWindow.logWriter();
		String path = Options.getLastDirectory().toString();
		JFileChooser fChooser = new JFileChooser(path);
		fChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fChooser.setCurrentDirectory(new File("."));
		fChooser.setDialogTitle("Select Neo4j database to import");
		int returnVal = fChooser.showOpenDialog(fMainWindow);
		if (returnVal != JFileChooser.APPROVE_OPTION) {
			return;
		}
		path = fChooser.getSelectedFile().toString();
		Options.setLastDirectory(new File(path).toPath());
		File selectedFile = fChooser.getSelectedFile();
		fSystemApi = UseSystemApi.create(pluginAction.getSession());
		performImport(selectedFile);
	}
	
	private void performImport(File selectedFile) {
		String message = String.format("Importing model from %s. Please wait.", selectedFile.getPath());
		JOptionPane pane = new JOptionPane(message, JOptionPane.WARNING_MESSAGE, 
				JOptionPane.DEFAULT_OPTION, null, new Object[]{}, null);
		JDialog dialog = pane.createDialog("Importing...");
		dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		new ImportTask(selectedFile, dialog).execute();
	}
	
	private class ImportTask extends SwingWorker<Boolean, Void> {
		
		private File file;
		private JDialog dialog;
		private GraphDatabaseService graphDb;
		
		ImportTask(File file, JDialog dialog) {
			this.file = file;
			this.dialog = dialog;
		}

		@Override
		protected Boolean doInBackground() throws Exception {
			EventQueue.invokeLater(new Runnable() {
				@Override
				public void run() {
					dialog.setVisible(true);
				}
			});
			graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(file);
			boolean res = importObjects(graphDb);
			return res;
		}
		
		@Override
		protected void done() {
			super.done();
			dialog.setVisible(false);
			dialog.dispose();
			try {
				graphDb.shutdown();
				if (get())
					JOptionPane.showMessageDialog(fMainWindow, 
							String.format("Model imported from %s successfully.", file.getPath()), "Success",
							JOptionPane.INFORMATION_MESSAGE);
				else 
					JOptionPane.showMessageDialog(fMainWindow, 
							"Unexpected errors occurred during import. See log pane for details.", "Import error",
							JOptionPane.ERROR_MESSAGE);
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
			catch (ExecutionException e) {
				e.printStackTrace();
			}
		}
		
		private boolean importObjects(GraphDatabaseService graphDb) {
			try (Transaction tx = graphDb.beginTx()) {
				ResourceIterator<Node> objNodes = graphDb.findNodes(Label.label("__Object"));
				while (objNodes.hasNext()) {
					Node node = objNodes.next();
					String cls = getOriginalClass(node, "__Object");
					if (cls == null) {
						fLogWriter.println(String.format("Error: Node with id %ld does not have a valid label representing its class", node.getId()));
						return false;
					}
					Object useName = node.getProperty("__name", null);
					if (useName == null) {
						fLogWriter.println(String.format("Error: Node with id %ld does not have the __name attribute.", node.getId()));
						return false;
					}
					if (!(useName instanceof String)) {
						fLogWriter.println(String.format("Error: The __name attribute of the node with id %ld is supposed to be a string!", node.getId()));
						return false;
					}
					String name = (String) useName;
					fLogWriter.println(String.format("Importing %s", name));
					try {
						MObject obj = fSystemApi.createObject(cls, name);
						for (String key : node.getPropertyKeys()) {
							fLogWriter.println(key);
							if (!key.equals("__name")) {
								boolean res = importAttribute(obj, key, node.getProperty(key));
								if (!res) return false;
							}
						}
					}
					catch (UseApiException e) {
						return false;
					}
				}
				tx.success();
			}
			return true;
		}
		
		private boolean importAttribute(MObject obj, String name, Object value) {
			String objName = obj.name();
			fLogWriter.println(String.format("Importing %s: %s...", objName, name));
			try {
				if (value instanceof Integer || value instanceof Double || value instanceof Boolean) {
					fSystemApi.setAttributeValue(objName, name, value.toString());
				}
				else if (value instanceof String) {
					Type t = obj.cls().attribute(name, true).type();
					// Value is actually an enum
					if (t.isKindOfEnum(VoidHandling.EXCLUDE_VOID))
						fSystemApi.setAttributeValue(objName, name, String.format("#%s", ((String) value)));
					// Value is a string
					else 
						fSystemApi.setAttributeValue(objName, name, String.format("'%s'", ((String) value)));
				}
				else if (value instanceof int[]) {
					String body = Arrays.stream((int[]) value).mapToObj(i -> Integer.toString(i)).collect(Collectors.joining(","));
					fSystemApi.setAttributeValue(objName, name, String.format("%s{%s}", getCollectionType(obj, name), body));
				}
				else if (value instanceof double[]) {
					String body = Arrays.stream((double[]) value).mapToObj(d -> Double.toString(d)).collect(Collectors.joining(","));
					fSystemApi.setAttributeValue(objName, name, String.format("%s{%s}", getCollectionType(obj, name), body));
				}
				else if (value instanceof boolean[]) {
					String body = Arrays.stream((Boolean[]) value).map(b -> b.toString()).collect(Collectors.joining(","));
					fSystemApi.setAttributeValue(objName, name, String.format("%s{%s}", getCollectionType(obj, name), body));
				}
				else if (value instanceof String[]) {
					Type elemType = ((CollectionType) obj.cls().attribute(name, true).type()).elemType();
					String body = "";
					if (elemType.isKindOfEnum(VoidHandling.EXCLUDE_VOID))
						body = Arrays.stream((String[]) value).map(s -> String.format("#%s", s)).collect(Collectors.joining(","));			
					else
						body = Arrays.stream((String[]) value).map(s -> String.format("'%s'", s)).collect(Collectors.joining(","));
					fSystemApi.setAttributeValue(objName, name, String.format("%s{%s}", getCollectionType(obj, name), body));
				}
				else {
					fLogWriter.println(String.format("Error when assigning %s.%s: Value must be a primitive/string or array of primitives/strings", objName, name));
					return false;
				}
			}
			catch (UseApiException e) {
				fLogWriter.println(String.format("Error when assigning %s.%s: %s", objName, name, e.getMessage()));
			}
			return true;
		}
		
		private String getCollectionType(MObject obj, String attrName) {
			Type type = obj.cls().attribute(attrName, true).type();
			if (type.isKindOfBag(VoidHandling.EXCLUDE_VOID))
				return "Bag";
			else if (type.isKindOfOrderedSet(VoidHandling.EXCLUDE_VOID))
				return "OrderedSet";
			else if (type.isKindOfSet(VoidHandling.EXCLUDE_VOID))
				return "Set";
			else return "Sequence";
		}
				
		private String getOriginalClass(Node node, String otherLabel) {
			for (Label l : node.getLabels()) {
				if (!l.name().equals(otherLabel)) {
					return l.name();
				}
			}
			return null;
		}
	}

}
