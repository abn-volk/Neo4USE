package org.uet.neo4use.tasks;

import java.awt.BorderLayout;
import java.io.File;
import java.net.URL;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.SwingWorker;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.gui.main.ViewFrame;
import org.uet.neo4use.views.ListenerDialog;

public class OpenForSyncTask extends SwingWorker<Void, Void> {

	private File selectedFile;
	private MainWindow fMainWindow;
	private UseSystemApi fSystemApi;
	private GraphDatabaseService graphDb;
	
	public OpenForSyncTask(File selectedFile, MainWindow fMainWindow, UseSystemApi fSystemApi) {
		this.selectedFile = selectedFile;
		this.fMainWindow = fMainWindow;
		this.fSystemApi = fSystemApi;
	}
	
	@Override
	protected Void doInBackground() throws Exception {
		graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(selectedFile);
		return null;
	}

	@Override
	protected void done() {
		super.done();
		ListenerDialog listener = new ListenerDialog(graphDb, fMainWindow.logWriter(), fSystemApi);
		URL url = getClass().getResource("/resources/ic_sync.png");
		ViewFrame vf = new ViewFrame("Neo4j synchronisation", listener, "");
		vf.setFrameIcon(new ImageIcon(url));
		vf.addInternalFrameListener(new InternalFrameListener() {
			@Override
			public void internalFrameActivated(InternalFrameEvent e) {
			}
			@Override
			public void internalFrameClosed(InternalFrameEvent e) {
				listener.detachModel();
			}
			@Override
			public void internalFrameClosing(InternalFrameEvent e) {
			}
			@Override
			public void internalFrameDeactivated(InternalFrameEvent e) {
			}
			@Override
			public void internalFrameDeiconified(InternalFrameEvent e) {
			}
			@Override
			public void internalFrameIconified(InternalFrameEvent e) {
			}
			@Override
			public void internalFrameOpened(InternalFrameEvent e) {
			}
		});
		JComponent c = (JComponent) vf.getContentPane();
		c.setLayout(new BorderLayout());
		c.add(listener, BorderLayout.CENTER);
		fMainWindow.addNewViewFrame(vf);
	}
}
