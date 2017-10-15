package org.uet.neo4use.views;

import java.awt.BorderLayout;
import java.io.PrintWriter;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.gui.views.View;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MLink;
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
		this.graphDb = graphDb;
		this.fLogWriter = fLogWriter;
		this.fSystemApi = fSystemApi;
		String msg = "Keep this dialog open to synchronise with the Neo4j database.";
		Icon icon = new ImageIcon("resources/ic_sync.png");
		JLabel label = new JLabel(msg, icon, JLabel.LEADING);
		add(label, BorderLayout.CENTER);
		fSystemApi.getSystem().getEventBus().register(this);
	}

	@Override
	public void detachModel() {
		graphDb.shutdown();
		fSystemApi.getSystem().getEventBus().unregister(this);
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
			node.delete();
			tx.success();
		}
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
	}
	
	@Subscribe
	public void onLinkDeleted(LinkDeletedEvent e) {
		MLink lnk = e.getLink();
		fLogWriter.println(String.format("Sync: Link %s deleted between %d objects.", e.getAssociation().name(), e.getAssociation().associationEnds().size()));
	}
}
