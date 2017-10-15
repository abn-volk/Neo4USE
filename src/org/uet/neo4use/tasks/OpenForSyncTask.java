package org.uet.neo4use.tasks;

import java.io.File;

import javax.swing.SwingWorker;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.gui.main.ViewFrame;
import org.tzi.use.gui.views.View;
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
		View listener = new ListenerDialog(graphDb, fMainWindow.logWriter(), fSystemApi);
		fMainWindow.addNewViewFrame(new ViewFrame("Neo4j synchronisation", listener, "resources/ic_sync.png"));
	}
}
