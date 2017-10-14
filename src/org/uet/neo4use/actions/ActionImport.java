package org.uet.neo4use.actions;

import java.awt.EventQueue;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.config.Options;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.ocl.type.CollectionType;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.type.Type.VoidHandling;
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
			if (importObjects(graphDb))
				if (importLinkObjects(graphDb))
					if (importLinks(graphDb))
						return true;
			return false;
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
					if (!importObject(node)) return false;
				}
				tx.success();
			}
			try (Transaction tx = graphDb.beginTx()) {
				ResourceIterator<Node> objNodes = graphDb.findNodes(Label.label("__Object"));
				while (objNodes.hasNext()) {
					Node node = objNodes.next();
					if (!importReferences(node)) return false;
				}
				tx.success();
			}
			return true;
		}
		
		private boolean importLinks(GraphDatabaseService graphDb) {
			try (Transaction tx = graphDb.beginTx()) {
				ResourceIterator<Node> linkNodes = graphDb.findNodes(Label.label("__Link"));
				while (linkNodes.hasNext()) {
					Node node = linkNodes.next();
					if (!importLink(node, false)) return false;
				}
				tx.success();
			}
			return true;
		}
		
		private boolean importLinkObjects(GraphDatabaseService graphDb) {
			try (Transaction tx = graphDb.beginTx()) {
				ResourceIterator<Node> linkObjNodes = graphDb.findNodes(Label.label("__LinkObject"));
				while (linkObjNodes.hasNext()) {
					Node node = linkObjNodes.next();
					if (!importLink(node, true)) return false;
				}
				tx.success();
			}
			try (Transaction tx = graphDb.beginTx()) {
				ResourceIterator<Node> objNodes = graphDb.findNodes(Label.label("__Object"));
				while (objNodes.hasNext()) {
					Node node = objNodes.next();
					if (!importReferences(node)) return false;
				}
				tx.success();
			}
			return true;
		}
		
		private boolean importObject(Node node) {
			String cls = getOriginalLabel(node, "__Object");
			if (cls == null) {
				fLogWriter.println(String.format("Error: Node with id %d does not have a valid label representing its class", node.getId()));
				return false;
			}
			Object useName = node.getProperty("__name", null);
			if (useName == null || !(useName instanceof String)) {
				fLogWriter.println(String.format("Error: Node with id %d must have the __name attribute with a string value!", node.getId()));
				return false;
			}
			String name = (String) useName;
			fLogWriter.println(String.format("Importing %s", name));
			try {
				MObject obj = fSystemApi.createObject(cls, name);
				if (!importAttributes(obj, node)) return false;
			}
			catch (UseApiException e) {
				fLogWriter.println(String.format("Error: %s", e.getMessage()));
				return false;
			}
			return true;
		}
		
		private boolean importAttributes(MObject obj, Node node) {
			// Import primitive attributes
			for (String key : node.getPropertyKeys()) {
				if (!key.equals("__name")) {
					boolean res = importAttribute(obj, key, node.getProperty(key));
					if (!res) return false;
				}
			}
			return true;
		}
		
		private boolean importReferences(Node node) {
			Object useName = node.getProperty("__name", null);
			if (useName == null || !(useName instanceof String)) {
				fLogWriter.println(String.format("Error: Node with id %d must have the __name attribute with a string value!", node.getId()));
				return false;
			}
			String name = (String) useName;
			MObject obj = fSystemApi.getObject(name); 
			if (obj == null) {
				fLogWriter.println(String.format("Error when importing %s's references: Object not found.", name));
				return false;
			}
			// Import object and collections of object
			Iterable<Relationship> relas = node.getRelationships(Direction.OUTGOING);
			Map<String, ArrayList<Relationship>> refs = new HashMap<String, ArrayList<Relationship>>();
			for (Relationship rela : relas) {
				// A single object reference
				if (!rela.hasProperty("__index")) {
					String attrName = rela.getType().name();
					Object objName = rela.getEndNode().getProperty("__name");
					if (objName == null || !(objName instanceof String)) {
						fLogWriter.println(String.format("Error: Node id %d has invalid __name.", rela.getEndNode().getId()));
						return false;
					}
					try {
						fSystemApi.setAttributeValue(name, attrName, objName.toString());
					}
					catch (UseApiException e) {
						fLogWriter.println(String.format("Error when assigning %s.%s: %s", name, objName.toString(), e.getMessage()));
						return false;
					}
				}
				// Collections of objects
				else {
					String type = rela.getType().name();
					if (rela.hasProperty("__index") && rela.getProperty("__index") instanceof Integer) {
						if (refs.get(type) == null) 
							refs.put(type, new ArrayList<Relationship>());
						refs.get(type).add(rela);
					}
					else {
						fLogWriter.println(String.format("Error when assigning %s.%s: Relationship must have a valid __index property.", name, type));
						return false;
					}
				}
			}
			// Assign collection attributes
			for (String attrName : refs.keySet()) {
				List<Relationship> relaList = refs.get(attrName);
				relaList.sort(new Comparator<Relationship>() {
					@Override
					public int compare(Relationship arg0, Relationship arg1) {
						return ((Integer) arg0.getProperty("__index")) - ((Integer) arg1.getProperty("__index"));
					}
				});
				try {
					String body = relaList.stream().map(n -> n.getEndNode().getProperty("__name").toString()).collect(Collectors.joining(","));
					fSystemApi.setAttributeValue(name, attrName, String.format("%s{%s}", getCollectionType(obj, attrName), body));
				}
				catch (NullPointerException e) {
					fLogWriter.println(String.format("Error when assigning %s.%s: One of the nodes doesn't have a valid __name.", obj.name(), attrName));
					return false;
				}
				catch (UseApiException e) {
					fLogWriter.println(String.format("Error when assigning %s.%s: %s", obj.name(), attrName, e.getMessage()));
					return false;
				}
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
				return false;
			}
			return true;
		}
		
		private boolean importLink(Node node, boolean isLinkObj) {
			String qualifierLabel = isLinkObj? "__LinkObject" : "__Link";
			String label = getOriginalLabel(node, qualifierLabel);
			if (label == null) {
				fLogWriter.println(String.format("Error: Node with id %d does not have a valid label representing its association type.", node.getId()));
				return false;
			}
			MAssociation asc = fSystemApi.getSystem().model().getAssociation(label);
			if (asc == null) {
				fLogWriter.println(String.format("Error: No association found for %s.", label));
				return false;
			}
			// Map relationships to link ends in the correct order (determined by rolenames)
			List<MAssociationEnd> ends = asc.associationEnds();
			Iterable<Relationship> relas = node.getRelationships(Direction.OUTGOING);
			Map<String, String> role2obj = new HashMap<>(ends.size());
			List<String> objNames = new ArrayList<>(ends.size());
			for (Relationship rela : relas ) {
				Object name = rela.getEndNode().getProperty("__name");
				if (name == null || !(name instanceof String)) {
					fLogWriter.println(String.format("Error: Node id %d has invalid __name.", rela.getEndNode().getId()));
					return false;
				}
				role2obj.put(rela.getType().name(), rela.getEndNode().getProperty("__name").toString());
			}
			for (MAssociationEnd end: ends) {
				String objName = role2obj.getOrDefault(end.name(), null);
				if (objName == null) {
					fLogWriter.println(String.format("Error: No object name found for rolename %s.", end.name()));
					return false;
				}
				objNames.add(objName);
			}
			try {
				String[] connectedObjectNames = objNames.toArray(new String[objNames.size()]);
				if (isLinkObj) {
					Object useName = node.getProperty("__name", null);
					if (useName == null || !(useName instanceof String)) {
						fLogWriter.println(String.format("Error: Node with id %d must have the __name attribute with a string value!", node.getId()));
						return false;
					}
					MObject obj = fSystemApi.createLinkObject(label, useName.toString(), connectedObjectNames);
					if (!importAttributes(obj, node)) return false;
				}
				else
					fSystemApi.createLink(label, connectedObjectNames);
			} catch (UseApiException e) {
				fLogWriter.println(String.format("Error: Exception when creating link or link object: %s", e.getMessage()));
				return false;
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
			else if (type.isKindOfSequence(VoidHandling.EXCLUDE_VOID))
				return "Sequence";
			else return null;
		}
				
		private String getOriginalLabel(Node node, String otherLabel) {
			for (Label l : node.getLabels()) {
				if (!l.name().equals(otherLabel)) {
					return l.name();
				}
			}
			return null;
		}
	}

}
