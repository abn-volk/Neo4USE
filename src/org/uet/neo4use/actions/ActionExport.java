package org.uet.neo4use.actions;

import java.awt.EventQueue;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.MultipleFoundException;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.config.Options;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.ocl.type.CollectionType;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.type.Type.VoidHandling;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.CollectionValue;
import org.tzi.use.uml.ocl.value.EnumValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.ObjectValue;
import org.tzi.use.uml.ocl.value.RealValue;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MLink;
import org.tzi.use.uml.sys.MLinkEnd;
import org.tzi.use.uml.sys.MLinkObject;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystemState;

public class ActionExport implements IPluginActionDelegate {
	
	private MainWindow fMainWindow;
	private PrintWriter fLogWriter;
	private UseSystemApi fSystemApi;
	private MSystemState fSystemState;
	
	@Override
	public void performAction(IPluginAction pluginAction) {
		fMainWindow = pluginAction.getParent();
		fLogWriter = fMainWindow.logWriter();
		fSystemApi = UseSystemApi.create(pluginAction.getSession());
		fSystemState = fSystemApi.getSystem().state();
		String path = Options.getLastDirectory().toString();
		JFileChooser fChooser = new JFileChooser(path);
		fChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fChooser.setCurrentDirectory(new File("."));
		fChooser.setDialogTitle("Select folder to export to");
		int returnVal = fChooser.showOpenDialog(fMainWindow);
		if (returnVal != JFileChooser.APPROVE_OPTION) {
			return;
		}
		path = fChooser.getSelectedFile().getPath();
		Options.setLastDirectory(fChooser.getSelectedFile().toPath());
		File selectedFile = fChooser.getSelectedFile();
		performExport(selectedFile);
	}

	private void performExport(File selectedFile) {
		String message = String.format("Exporting model into %s. Please wait.", selectedFile.getPath());
		JOptionPane pane = new JOptionPane(message, JOptionPane.WARNING_MESSAGE, 
				JOptionPane.DEFAULT_OPTION, null, new Object[]{}, null);
		JDialog dialog = pane.createDialog("Exporting...");
		dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		ExportTask task = new ExportTask(selectedFile, dialog);
		task.execute();
	}
	
	private class ExportTask extends SwingWorker<Boolean, Void> {

		private File file;
		private JDialog dialog;
		private GraphDatabaseService graphDb;

		ExportTask (File file, JDialog dialog) {
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
			Set<MObject> objects = fSystemState.allObjects();
			objects.forEach(obj -> {
				if (!obj.cls().isKindOfAssociation(VoidHandling.EXCLUDE_VOID))
					createObjectIfNotExists(obj);
			});
			Set<MLink> links = fSystemState.allLinks();
			for (MLink lnk: links) {
				if (lnk.association().isKindOfClass(VoidHandling.EXCLUDE_VOID)) {
					if (!createLinkObject((MLinkObject) lnk)) 
						return false;
				}
				else { 
					if (!createLink(lnk))
						return false;
				}
			}
			return true;
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
							String.format("Model exported to %s.", file.getPath()), "Success",
							JOptionPane.INFORMATION_MESSAGE);
				else 
					JOptionPane.showMessageDialog(fMainWindow, 
							"Unexpected errors occurred during export. See log pane for details.", "Export error",
							JOptionPane.ERROR_MESSAGE);
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
			catch (ExecutionException e) {
				e.printStackTrace();
			}
		}
		
		private Node createObjectIfNotExists(MObject obj) {
			String name = obj.name();
			Label label = Label.label(obj.cls().name());
			try (Transaction tx = graphDb.beginTx()) {
				ResourceIterator<Node> nodes = graphDb.findNodes(label, "__name", name);
				if (nodes.hasNext()) {
					tx.success();
					return nodes.next();
				}
				else tx.success();
			}
			try (Transaction tx = graphDb.beginTx()) {
				fLogWriter.println(String.format("Creating object %s...", obj.name()));
				Node node = graphDb.createNode();
				node.addLabel(Label.label(obj.cls().name()));
				Map<MAttribute,Value> avMap = obj.state(fSystemState).attributeValueMap();
				node.setProperty("__name", obj.name());
				avMap.forEach((a,v) -> setProperty(node, a, v));
				tx.success();
				return node;
			}
		}
		
		private boolean createLink(MLink lnk) {
			String assocName = lnk.association().name();
			fLogWriter.println(String.format("Creating link: %s", lnk.association().name()));			
			try (Transaction tx = graphDb.beginTx()) {
				Node linkNode = graphDb.createNode(Label.label("__Link"), Label.label(assocName));
				Set<MLinkEnd> ends = lnk.linkEnds();
				for (MLinkEnd e : ends) {
					MObject obj = e.object();
					String rolename = e.associationEnd().nameAsRolename();
					Node objNode = graphDb.findNode(Label.label(obj.cls().name()), "__name", obj.name());
					linkNode.createRelationshipTo(objNode, RelationshipType.withName(rolename));
				}
				tx.success();
				return true;
			}
			catch (MultipleFoundException e) {
				fLogWriter.println("Error when creating link: Multiple node found for an object.");
				return false;
			}
		}
		
		private boolean createLinkObject(MLinkObject lnk) {
			String assocName = lnk.association().name();
			fLogWriter.println(String.format("Creating link object: %s:%s", lnk.name(), lnk.association().name()));			
			try (Transaction tx = graphDb.beginTx()) {
				Node linkNode = graphDb.createNode(Label.label("__LinkObject"), Label.label(assocName));
				Set<MLinkEnd> ends = lnk.linkEnds();
				for (MLinkEnd e : ends) {
					MObject obj = e.object();
					String rolename = e.associationEnd().nameAsRolename();
					Node objNode = graphDb.findNode(Label.label(obj.cls().name()), "__name", obj.name());
					linkNode.createRelationshipTo(objNode, RelationshipType.withName(rolename));
				}
				Map<MAttribute,Value> avMap = lnk.state(fSystemState).attributeValueMap();
				avMap.forEach((a,v) -> setProperty(linkNode, a, v));
				tx.success();
				return true;
			}
			catch (MultipleFoundException e) {
				fLogWriter.println("Error when creating link: Multiple node found for an object.");
				return false;
			}
		}
		
		private void setProperty(Node node, MAttribute attr, Value val) {
			Type type = val.type();
			String attrName = attr.name();
			if (!type.isVoidOrElementTypeIsVoid() && val.isDefined()) {
				if (type.isKindOfInteger(VoidHandling.EXCLUDE_VOID)) {
					node.setProperty(attrName, ((IntegerValue) val).value());
				}
				else if (type.isKindOfReal(VoidHandling.EXCLUDE_VOID)) {
					node.setProperty(attrName, ((RealValue) val).value());
				}
				else if (type.isKindOfString(VoidHandling.EXCLUDE_VOID)) {
					node.setProperty(attrName, ((StringValue) val).value());
				}
				else if (type.isKindOfBoolean(VoidHandling.EXCLUDE_VOID)) {
					node.setProperty(attrName, ((BooleanValue) val).value());
				}
				else if (type.isKindOfEnum(VoidHandling.EXCLUDE_VOID)) {
					node.setProperty(attrName, ((EnumValue) val).value());
				}
				else if (type.isKindOfCollection(VoidHandling.EXCLUDE_VOID)) {
					CollectionType cType = (CollectionType) type;
					Type elemType = cType.elemType();
					Collection<Value> values = ((CollectionValue) val).collection();
					
					if (elemType.isKindOfInteger(VoidHandling.EXCLUDE_VOID)) {
						ArrayList<Integer> ints = new ArrayList<Integer>();
						for (Value v : values) {
							ints.add(((IntegerValue) v).value());
						}
						
						int[] x = new int[ints.size()];
						for (int i=0; i<ints.size(); i++) {
							x[i] = ints.get(i);
						}
						node.setProperty(attrName, x);
					}
					else if (elemType.isKindOfReal(VoidHandling.EXCLUDE_VOID)){
						ArrayList<Double> dous = new ArrayList<Double>();
						for (Value v : values) {
							dous.add(((RealValue) v).value());
						}
						double[] x = new double[dous.size()];
						for (int i=0; i<dous.size(); i++) {
							x[i] = dous.get(i);
						}
						node.setProperty(attrName, x);
					}
					else if (elemType.isKindOfString(VoidHandling.EXCLUDE_VOID)){
						ArrayList<String> strs = new ArrayList<>();
						for (Value v: values) {
							strs.add(((StringValue) v).value());
						}
						String[] strArray = new String[strs.size()];
						strs.toArray(strArray);
						node.setProperty(attrName, strArray);
					}
					else if (elemType.isKindOfEnum(VoidHandling.EXCLUDE_VOID)){
						ArrayList<String> strs = new ArrayList<>();
						for (Value v: values) {
							strs.add(((EnumValue) v).value());
						}
						String[] strArray = new String[strs.size()];
						strs.toArray(strArray);
						node.setProperty(attrName, strArray);
					}
					else if (elemType.isKindOfBoolean(VoidHandling.EXCLUDE_VOID)){
						ArrayList<Boolean> bools = new ArrayList<>();
						for (Value v: values) {
							bools.add(((BooleanValue) v).value());
						}
						boolean[] boolArray = new boolean[bools.size()];
						for (int i=0; i<bools.size(); i++) {
							boolArray[i] = bools.get(i);
						}
						node.setProperty(attrName, boolArray);
					}
					else if (elemType.isKindOfClass(VoidHandling.EXCLUDE_VOID)) {
						int i = 0;
						for (Value v : values) {
							MObject obj = ((ObjectValue) v).value();
							Node newObj = createObjectIfNotExists(obj);
							try (Transaction tx = graphDb.beginTx()) {
								Relationship rela = node.createRelationshipTo(newObj, 
										RelationshipType.withName(attrName));
								rela.setProperty("__index", i);
								i++;
								tx.success();
							}
						}
					}
				}
				else if (type.isKindOfClass(VoidHandling.EXCLUDE_VOID)) {
					ObjectValue v = (ObjectValue) val;
					Node newObj = createObjectIfNotExists(v.value());
					try (Transaction tx = graphDb.beginTx()) {
						node.createRelationshipTo(newObj, RelationshipType.withName(attrName));
						tx.success();
					}
				}
			}
	}
		
	}

}