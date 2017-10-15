package org.uet.neo4use.actions;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.PrintWriter;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

import org.tzi.use.api.UseSystemApi;
import org.tzi.use.config.Options;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.tzi.use.uml.sys.MSystemState;
import org.uet.neo4use.tasks.ExportTask;

public class ActionExport implements IPluginActionDelegate {

	
	@Override
	public void performAction(IPluginAction pluginAction) {
		// Get necessary USE components
		MainWindow fMainWindow = pluginAction.getParent();
		PrintWriter fLogWriter = fMainWindow.logWriter();
		MSystemState fSystemState = UseSystemApi.create(pluginAction.getSession()).getSystem().state();
		// Show file chooser dialog
		String path = Options.getLastDirectory().toString();
		JFileChooser fChooser = new JFileChooser(path);
		fChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fChooser.setCurrentDirectory(new File("."));
		fChooser.setDialogTitle("Select folder to export to");
		int returnVal = fChooser.showOpenDialog(fMainWindow);
		if (returnVal != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File selectedFile = fChooser.getSelectedFile();
		Options.setLastDirectory(selectedFile.toPath());
		// Start exporting
		String message = String.format("Exporting model into %s. Please wait.", selectedFile.getPath());
		JOptionPane pane = new JOptionPane(message, JOptionPane.WARNING_MESSAGE, 
				JOptionPane.DEFAULT_OPTION, null, new Object[]{}, null);
		JDialog dialog = pane.createDialog("Exporting...");
		dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		ExportTask task = new ExportTask(selectedFile, fMainWindow, fLogWriter, fSystemState);
		task.addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				if ("state".equals(event.getPropertyName())
		                 && SwingWorker.StateValue.DONE == 
		                  event.getNewValue()) {
		             dialog.setVisible(false);
		             dialog.dispose();
		         }	
			}
		});
		task.execute();
		dialog.setVisible(true);
	}

}