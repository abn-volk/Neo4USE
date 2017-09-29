package org.uet.neo4use.actions;

import java.io.File;
import java.io.PrintWriter;

import javax.swing.JFileChooser;

import org.tzi.use.config.Options;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;

public class ActionImport implements IPluginActionDelegate {
	
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
		fChooser.setDialogTitle("Select Neo4j database to import");
		int returnVal = fChooser.showOpenDialog(fMainWindow);
		if (returnVal != JFileChooser.APPROVE_OPTION) {
			return;
		}
		path = fChooser.getSelectedFile().toString();
		Options.setLastDirectory(new File(path).toPath());
		File selectedFile = fChooser.getSelectedFile();
	}

}
