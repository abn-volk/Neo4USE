package org.uet.neo4use.actions;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.MultipleFoundException;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.ocl.type.CollectionType;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.type.Type.VoidHandling;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.CollectionValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.RealValue;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MLink;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystemState;

public class ActionExport implements IPluginActionDelegate{
	
	private GraphDatabaseService graphDb;
	private UseSystemApi fSystemApi;
	private PrintWriter fLogWriter;
	private MSystemState fSystemState;

	@Override
	public void performAction(IPluginAction pluginAction) {
		graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(new File("databases/my_graph.db"));
		fSystemApi = UseSystemApi.create(pluginAction.getSession());
		fSystemState = fSystemApi.getSystem().state();
		fLogWriter = pluginAction.getParent().logWriter();
		Set<MObject> objects = fSystemState.allObjects();
		objects.forEach(obj -> createObject(obj));
		Set<MLink> links = fSystemState.allLinks();
		links.forEach(lnk -> createLink(lnk));
		fLogWriter.println("Neo4J export complete.");
		graphDb.shutdown();
	}
	
	private void createObject(MObject obj) {
		try (Transaction tx = graphDb.beginTx()) {
			fLogWriter.println(String.format("Creating object %s...", obj.name()));
			Node node = graphDb.createNode();
			node.addLabel(Label.label(obj.cls().name()));
			Map<MAttribute,Value> avMap = obj.state(fSystemState).attributeValueMap();
			node.setProperty("__name", obj.name());
			avMap.forEach((a,v) -> setProperty(node, a, v));
			tx.success();
		}
	}
	
	private void createLink(MLink lnk) {
		String assocName = lnk.association().name();
		List<MObject> linkedObjs = lnk.linkedObjects();
		MObject obj1 = null;
		MObject obj2 = null;
		if (linkedObjs.size() == 2) {
			obj1 = linkedObjs.get(0);
			obj2 = linkedObjs.get(1);
		}
		else if (linkedObjs.size() == 1) {
			obj1 = obj2 = linkedObjs.get(0);
		}
		else {
			fLogWriter.println("Error when creating link: Links must have one or two ends!");
			return;
		}
		try (Transaction tx = graphDb.beginTx()) {
			Node node1 = graphDb.findNode(Label.label(obj1.cls().name()), "__name", obj1.name());
			Node node2 = graphDb.findNode(Label.label(obj2.cls().name()), "__name", obj2.name());
			RelationshipType relaType = RelationshipType.withName(assocName);
			node1.createRelationshipTo(node2, relaType);
			tx.success();
		}
		catch (MultipleFoundException e) {
			fLogWriter.println("Error when creating link: More than one object found for " + obj1.name() + " or " + obj2.name());
			return;
		}
	}
	
	private void setProperty(PropertyContainer node, MAttribute attr, Value val) {
		Type type = val.type();
		if (!type.isVoidOrElementTypeIsVoid() && val.isDefined()) {
			if (type.isKindOfInteger(VoidHandling.EXCLUDE_VOID)) {
				node.setProperty(attr.name(), ((IntegerValue) val).value());
			}
			else if (type.isKindOfReal(VoidHandling.EXCLUDE_VOID)) {
				node.setProperty(attr.name(), ((RealValue) val).value());
			}
			else if (type.isKindOfString(VoidHandling.EXCLUDE_VOID)) {
				node.setProperty(attr.name(), ((StringValue) val).value());
			}
			else if (type.isKindOfBoolean(VoidHandling.EXCLUDE_VOID)) {
				node.setProperty(attr.name(), ((BooleanValue) val).value());
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
					node.setProperty(attr.name(), x);
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
					node.setProperty(attr.name(), x);
				}
				else if (elemType.isKindOfString(VoidHandling.EXCLUDE_VOID)){
					ArrayList<String> strs = new ArrayList<>();
					for (Value v: values) {
						strs.add(((StringValue) v).value());
					}
					String[] strArray = new String[strs.size()];
					strs.toArray(strArray);
					node.setProperty(attr.name(), strArray);
				}
			}
		}
	}

}
