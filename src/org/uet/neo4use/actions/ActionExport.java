package org.uet.neo4use.actions;

import java.awt.EventQueue;
import java.io.File;
import java.io.PrintWriter;
import java.util.concurrent.ExecutionException;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.tzi.use.config.Options;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;

public class ActionExport implements IPluginActionDelegate {
	
	private MainWindow fMainWindow;
	private PrintWriter fLogWriter;
	
	@Override
	public void performAction(IPluginAction pluginAction) {
		fMainWindow = pluginAction.getParent();
		fLogWriter = fMainWindow.logWriter();
		String path = Options.getLastDirectory().toString();
		JFileChooser fChooser = new JFileChooser(path);
		fChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fChooser.setCurrentDirectory(new File("."));
		fChooser.setDialogTitle("Select folder to export to");
		int returnVal = fChooser.showOpenDialog(fMainWindow);
		if (returnVal != JFileChooser.APPROVE_OPTION) {
			return;
		}
		path = fChooser.getSelectedFile().toString();
		Options.setLastDirectory(new File(path).toPath());
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
			GraphDatabaseService graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(file);
			// TODO: Implement exporting here
			return true;
		}
		
		@Override
		protected void done() {
			super.done();
			dialog.setVisible(false);
			dialog.dispose();
			try {
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
		
	}

}