package org.uet.neo4use.actions;

import java.io.File;
import java.io.PrintWriter;

import javax.swing.JFileChooser;

import org.tzi.use.config.Options;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;

public class ActionExport implements IPluginActionDelegate {
	private File selectedFile;

	@Override
	public void performAction(IPluginAction pluginAction) {
		MainWindow fMainWindow = pluginAction.getParent();
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
		selectedFile = fChooser.getSelectedFile();
		fLogWriter.printf("Selected path is %s", path);
	}

}