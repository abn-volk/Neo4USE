package org.uet.neo4use.actions;

import java.io.File;
import java.io.PrintWriter;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

import org.tzi.use.config.Options;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;

public class ActionExport implements IPluginActionDelegate {
	
	private MainWindow fMainWindow;
	
	@Override
	public void performAction(IPluginAction pluginAction) {
		fMainWindow = pluginAction.getParent();
		String path = Options.getLastDirectory().toString();
		JFileChooser fChooser = new JFileChooser(path);
		PrintWriter fLogWriter = fMainWindow.logWriter();
		fChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fChooser.setCurrentDirectory(new File("."));
		fChooser.setDialogTitle("Select folder");
		int returnVal = fChooser.showOpenDialog(fMainWindow);
		if (returnVal != JFileChooser.APPROVE_OPTION) {
			return;
		}
		path = fChooser.getSelectedFile().toString();
		Options.setLastDirectory(new File(path).toPath());
		File selectedFile = fChooser.getSelectedFile();
		fLogWriter.printf("Selected path is %s", path);	
		performExport(selectedFile);
	}

	private void performExport(File selectedFile) {
		String message = String.format("Exporting model into %s. Please wait.", selectedFile.getPath());
		JOptionPane pane = new JOptionPane(message, JOptionPane.WARNING_MESSAGE, 
				JOptionPane.DEFAULT_OPTION, null, new Object[]{}, null);
		JDialog dialog = pane.createDialog("Exporting...");
		dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		dialog.setVisible(true);
		ExportTask task = new ExportTask(selectedFile, dialog);
		task.execute();
		
	}
	
	private class ExportTask extends SwingWorker<Void, Void> {
		
		private File file;
		private JDialog dialog;

		ExportTask (File file, JDialog dialog) {
			this.file = file;
			this.dialog = dialog;
		}
		
		@Override
		protected Void doInBackground() throws Exception {			
			return null;
		}
		
	}

}