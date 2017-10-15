package org.uet.neo4use.views;

import java.awt.BorderLayout;
import java.io.PrintWriter;
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
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.gui.views.View;
import org.tzi.use.uml.mm.MAttribute;
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
		String msg = "Keep this dialog open to synchronise with the Neo4j database.";
		Icon icon = new ImageIcon("resources/ic_sync.png");
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
			tx.success();
		}
	}
	
	@Subscribe
    public void onObjectDestroyed(ObjectDestroyedEvent e) {
		MObject obj = e.getDestroyedObject();
		fLogWriter.println(String.format("Sync: Object %s destroyed.", obj.name()));
		try(Transaction tx = graphDb.beginTx()) {
			Node node = graphDb.findNode(Label.label(obj.cls().name()), "__name", obj.name());
			deleteNode(node);
			tx.success();
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
		MAttribute attr = e.getAttribute();
		Value val = e.getValue();
		fLogWriter.println(String.format("Sync: Assignment %s.%s = %s", obj.name(), attr.name(), val.toStringWithType()));
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
