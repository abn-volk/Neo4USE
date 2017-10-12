package org.uet.neo4use.actions;

import java.awt.EventQueue;
import java.io.File;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.config.Options;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
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
		JDialog dialog = pane.createDialog("Exporting...");
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
			
			return null;
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
				try {
					MObject obj = fSystemApi.createObject(cls, name);
					Map<String, Object> props = node.getProperties();
					for (Map.Entry<String, Object> prop : props.entrySet()) {
						if (!prop.getKey().equals("__name")) {
							if (!importAttribute(obj, prop.getKey(), prop.getValue()))
								return false;
						}
					}
					return true;
				}
				catch (UseApiException e) {
					return false;
				}
			}
			
			return true;
		}
		
		private boolean importAttribute(MObject obj, String name, Object value) {
			return true;
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
