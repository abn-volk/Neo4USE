package org.uet.neo4use.views;

import java.awt.BorderLayout;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.MultipleFoundException;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.gui.views.View;
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
import org.tzi.use.uml.sys.events.AttributeAssignedEvent;
import org.tzi.use.uml.sys.events.LinkDeletedEvent;
import org.tzi.use.uml.sys.events.LinkInsertedEvent;
import org.tzi.use.uml.sys.events.ObjectCreatedEvent;
import org.tzi.use.uml.sys.events.ObjectDestroyedEvent;

import com.google.common.eventbus.Subscribe;

public class ListenerDialog extends JPanel implements View {
	
	private static final long serialVersionUID = 7846056939302698944L;
	private GraphDatabaseService graphDb;
	private PrintWriter fLogWriter;
	private UseSystemApi fSystemApi;
	
	public ListenerDialog(GraphDatabaseService graphDb, PrintWriter fLogWriter, UseSystemApi fSystemApi) {
		super(new BorderLayout());
		this.graphDb = graphDb;
		this.fLogWriter = fLogWriter;
		this.fSystemApi = fSystemApi;
		fSystemApi.getSystem().getEventBus().register(this);
		setLayout(new BorderLayout());
		String msg = "Keep this dialog open to synchronise with the Neo4j database.";
		Icon icon = new ImageIcon(getClass().getResource("/resources/ic_sync.png"));
		JLabel label = new JLabel(msg, icon, JLabel.LEADING);
		add(label, BorderLayout.CENTER);
		setVisible(true);
	}
	
	@Subscribe
    public void onObjectCreated(ObjectCreatedEvent e) {
		MObject obj = e.getCreatedObject();
		fLogWriter.println(String.format("Sync: Object %s added.", obj.name()));
		try(Transaction tx = graphDb.beginTx()) {
			Node node = graphDb.createNode(Label.label("__Object"), Label.label(obj.cls().name()));
			String objName = obj.name();
			node.setProperty("__name", objName);
			fLogWriter.println("Neo4j node created.");
			tx.success();
		}
	}
	
	@Subscribe
    public void onObjectDestroyed(ObjectDestroyedEvent e) {
		MObject obj = e.getDestroyedObject();
		fLogWriter.println(String.format("Sync: Object %s destroyed.", obj.name()));
		if (!(obj instanceof MLinkObject)) {
			try(Transaction tx = graphDb.beginTx()) {
				try {
					Node node = graphDb.findNode(Label.label(obj.cls().name()), "__name", obj.name());
					if (node != null) deleteNode(node);
					tx.success();
				}
				catch (MultipleFoundException ex) {
					fLogWriter.println(String.format("Error: Multiple node found for %s.", obj.name()));
				}
				catch (NotFoundException ignored) {} 
			}
		}
	}
	
	private void deleteNode(Node node) {
		for (Relationship rela : node.getRelationships()) {
			// This is a link, delete the link node too
			if (rela.getEndNodeId() == node.getId()) {
				deleteNode(rela.getStartNode());
			}
			rela.delete();
		}
		node.delete();
	}
	
	@Subscribe
    public void onAttributeAssignment(AttributeAssignedEvent e) {
		MObject obj = e.getObject();
		boolean isLinkObject = obj instanceof MLinkObject;
		MAttribute attr = e.getAttribute();
		Value val = e.getValue();
		try (Transaction tx = graphDb.beginTx()) {
			if (isLinkObject) {
				MLinkObject lnk = (MLinkObject) obj;
				ResourceIterator<Node> nodes = graphDb.findNodes(Label.label(lnk.association().name()));
				while (nodes.hasNext()) {
					Node node = nodes.next();
					if (matchNodeToLink(node, lnk.linkEnds())) {
						setProperty(node, attr, val);
					}
				}
			}
			else {
				Node node = graphDb.findNode(Label.label(obj.cls().name()), "__name", obj.name());
				setProperty(node, attr, val);
			}
			tx.success();
		}
		fLogWriter.println(String.format("Sync: Assignment %s.%s = %s", obj.name(), attr.name(), val.toStringWithType()));
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
	
	private Node createObjectIfNotExists(MObject obj) {
		String name = obj.name();
		Label label = Label.label(obj.cls().name());
		try (Transaction tx = graphDb.beginTx()) {
			try {
				Node node = graphDb.findNode(label, "__name", name);
				if (node != null) {
					tx.success();
					return node;
				}
				tx.success();
			}
			catch (MultipleFoundException e) {
				fLogWriter.println(String.format("Error: Multiple node found for %s.", name));
			}
		}
		try (Transaction tx = graphDb.beginTx()) {
			fLogWriter.println(String.format("Creating object %s...", obj.name()));
			Node node = graphDb.createNode(label, Label.label("__Object"));
			node.setProperty("__name", obj.name());
			tx.success();
			return node;
		}
	}
	
	@Subscribe
	public void onLinkInserted(LinkInsertedEvent e) {
		MLink lnk = e.getLink();
		fLogWriter.println(String.format("Sync: Link %s added between %d objects.", e.getAssociation().name(), e.getAssociation().associationEnds().size()));
		boolean isLinkObject = lnk instanceof MLinkObject;
		Set<MLinkEnd> linkEnds = lnk.linkEnds();
		try (Transaction tx = graphDb.beginTx()) {
			String qualifierLabel = isLinkObject? "__LinkObject" : "__Link";
			Node linkNode = graphDb.createNode(Label.label(qualifierLabel), Label.label(lnk.association().name()));
			for (MLinkEnd end : linkEnds) {
				MObject endObj = end.object();
				try {
					Node endNode = graphDb.findNode(Label.label(endObj.cls().name()), "__name", endObj.name());
					linkNode.createRelationshipTo(endNode, RelationshipType.withName(end.associationEnd().name()));
				}
				catch (MultipleFoundException ex) {
					fLogWriter.println(String.format("Error: Multiple node found for %s.", endObj.name()));
					tx.failure();
				}
			}
			fLogWriter.println("Neo4j node and links created successfully.");
			tx.success();
		}
	}
	
	@Subscribe
	public void onLinkDeleted(LinkDeletedEvent e) {
		MLink lnk = e.getLink();
		fLogWriter.println(String.format("Sync: Link %s deleted between %d objects.", e.getAssociation().name(), e.getAssociation().associationEnds().size()));
		try (Transaction tx = graphDb.beginTx()) {
			ResourceIterator<Node> nodes = graphDb.findNodes(Label.label(e.getAssociation().name()));
			while (nodes.hasNext()) {
				Node node = nodes.next();
				if (matchNodeToLink(node, lnk.linkEnds())) {
					deleteNode(node);
				}
			}
		}
		catch (NotFoundException ignored) {} 
	}
	
	private boolean matchNodeToLink(Node node, Set<MLinkEnd> ends) {
		for (MLinkEnd end : ends) {
			boolean hasMatch = false;
			for (Relationship rela : node.getRelationships(Direction.OUTGOING)) {
				if (rela.getType().name().equals(end.associationEnd().nameAsRolename())) {
					Object endNodeName = rela.getEndNode().getProperty("__name", null);
					if (endNodeName instanceof String && end.object().name().equals((String) endNodeName)) {
						hasMatch = true;
					}
					else
						return false;
				}
			}
			if (!hasMatch) return false;
		}
		return true;
	}

	@Override
	public void detachModel() {
		fLogWriter.println("Shutting down database...");
		fSystemApi.getSystem().getEventBus().unregister(this);
		graphDb.shutdown();
	}
}
