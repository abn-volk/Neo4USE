package org.uet.neo4use.actions;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.IntToDoubleFunction;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
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
		fLogWriter.println("Neo4J export complete.");
		graphDb.shutdown();
	}
	
	private void createObject(MObject obj) {
		try (Transaction tx = graphDb.beginTx()) {
			fLogWriter.println(String.format("Creating object %s...", obj.name()));
			Node node = graphDb.createNode();
			node.addLabel(Label.label(obj.cls().name()));
			Map<MAttribute,Value> avMap = obj.state(fSystemState).attributeValueMap();
			avMap.forEach((a,v) -> setObjectAttribute(node, obj, a, v));
			tx.success();
		}
	}
	
	private void setObjectAttribute(Node node, MObject obj, MAttribute attr, Value val) {
		node.setProperty("use_node_name", obj.name());
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
					
					Double[] x = new Double[dous.size()];
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
