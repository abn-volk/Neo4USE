package org.uet.neo4use.actions;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

import org.tzi.use.config.Options;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.gui.util.ExtFileFilter;
import org.tzi.use.main.shell.Shell;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;

public class ActionOpenScript implements IPluginActionDelegate {
	private File selectedFile;

	@Override
	public void performAction(IPluginAction pluginAction) {
		MainWindow fMainWindow = pluginAction.getParent();
		
		JDialog fDialog = new JDialog(fMainWindow, "Open USE script");
		JTextField fFileName = new JTextField();
		
		JButton btnBrowse = new JButton("Browse");
		btnBrowse.setMnemonic('B');
		btnBrowse.addActionListener(new ActionListener() {
			private JFileChooser fChooser;
			
			public void actionPerformed(ActionEvent e) {
				String path;
				if (fChooser == null) {
					path = Options.getLastDirectory().toString();
					fChooser = new JFileChooser(path);
					ExtFileFilter filter = new ExtFileFilter(new String[] {"soil",  "cmd"}, "USE scripts");
					fChooser.setFileFilter(filter);
					fChooser.setDialogTitle("Open USE script");
				}
				int returnVal = fChooser.showOpenDialog(fDialog);
				if (returnVal != JFileChooser.APPROVE_OPTION)
					return;
				path = fChooser.getCurrentDirectory().toString();
				Options.setLastDirectory(new File(path).toPath());
				fFileName.setText(Paths.get(path,
						fChooser.getSelectedFile().getName()).toString());
				selectedFile = fChooser.getSelectedFile();
			}
		});
		
		JButton btnOpen = new JButton("Open");
		btnOpen.setMnemonic('O');
		btnOpen.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (selectedFile == null) {
					JOptionPane.showMessageDialog(fDialog, "Select a file to continue.", "No file selected", JOptionPane.ERROR_MESSAGE);
				} 
				else {
					try (Stream<String> stream = Files.lines(selectedFile.toPath())) {
						fMainWindow.logWriter().println("Opening " + selectedFile.getPath().toString() + "...");
						stream.forEach(line -> {Shell.getInstance().processLineSafely(line);});
					}
					catch (IOException ignored) {}
					
					fDialog.setVisible(false);
					fDialog.dispose();
				}
			}
		});
		
		JComponent contentPane = (JComponent) fDialog.getContentPane();
		contentPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.LINE_AXIS));
		contentPane.add(Box.createHorizontalGlue());
		contentPane.add(fFileName);
		contentPane.add(Box.createRigidArea(new Dimension(10, 0)));
		contentPane.add(btnBrowse);
		contentPane.add(Box.createRigidArea(new Dimension(10, 0)));
		contentPane.add(btnOpen);
		fDialog.getRootPane().setDefaultButton(btnOpen);
		fDialog.pack();
		fDialog.setSize(new Dimension(400, 80));
		fDialog.setResizable(true);
		fDialog.setLocationRelativeTo(fMainWindow);
		
		fDialog.setVisible(true);
	}

}
