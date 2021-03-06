package immibis.beardedoctonemesis.gui;

import immibis.beardedoctonemesis.IProgressListener;
import immibis.beardedoctonemesis.Main;
import immibis.beardedoctonemesis.mcp.McpMapping;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.Preferences;

import javax.swing.*;

public class GuiMain extends JFrame {
	private static final long serialVersionUID = 1;
	
	// The Java Preferences API is used to store the last directory the user was browsing
	// for the input/output files (PREFS_KEY_BROWSEDIR)
	// and the selected MCP directory (PREFS_KEY_MCPDIR).
	// Prefs are saved when the user clicks "Go" or closes the window.
	private final Preferences prefs = Preferences.userNodeForPackage(GuiMain.class);
	private final static String PREFS_KEY_BROWSEDIR = "browseDir";
	private final static String PREFS_KEY_MCPDIR = "mcpDir";
	
	private JComboBox<Operation> opSelect;
	private JComboBox<Side>sideSelect;
	private JTextField inputField, outputField, mcpField;
	private JButton goButton;
	private JProgressBar progressBar;
	private JLabel progressLabel;
	
	private Thread curTask = null;
	
	// the last directory the user was browsing, for the input/output files
	private final Reference<File> browseDir = new Reference<File>();
	// the last directory the user was browsing, for the MCP directory
	private final Reference<File> mcpBrowseDir = new Reference<File>();
	
	private void savePrefs() {
		prefs.put(PREFS_KEY_BROWSEDIR, browseDir.val.toString());
		prefs.put(PREFS_KEY_MCPDIR, mcpField.getText());
	}
	
	synchronized void goButtonPressed() {
		
		if(curTask != null && curTask.isAlive())
			return;
		
		savePrefs();
		
		//final Operation op = (Operation)opSelect.getSelectedItem();
		final Side side = (Side)sideSelect.getSelectedItem();
		
		final File mcpDir = new File(mcpField.getText());
		final File confDir = new File(mcpDir, "conf");
		final String[] xpathlist = side.xpath.split(File.pathSeparator);
		
		String error = null;
		
		if(!mcpDir.isDirectory())
			error = "MCP folder not found (at "+mcpDir+")";
		else if(!confDir.isDirectory())
			error = "'conf' folder not found in MCP folder (at "+confDir+")";
		else
		{
			for(int k = 0; k < xpathlist.length; k++)
			{
				String path = xpathlist[k];
				File xpathfile = new File(mcpDir, path);
				if(!xpathfile.isFile())
				{
					error = "'" + path + "' not found in MCP folder (at "+xpathfile+")";
					if(xpathfile.toString().endsWith("_reobf.jar"))
						error += "\n\nYou need to reobfuscate before using BON.";
					break;
				}
				xpathlist[k] = xpathfile.getAbsolutePath();
			}
		}
		
		if(error != null)
		{
			JOptionPane.showMessageDialog(this, error, "BON - Error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		progressBar.setValue(0);
		
		curTask = new Thread() {
			public void run() {
				try {
					McpMapping mcp = new McpMapping(confDir, side.mcpside, false);
					
					Main m = new Main();
					m.input = new File(inputField.getText());
					m.output = new File(outputField.getText());
					m.map = mcp.getMapping();
					m.xpathlist = xpathlist;
					m.progress = new IProgressListener() {
						@Override
						public void start(final int max, final String text) {
							SwingUtilities.invokeLater(new Runnable() {
								public void run() {
									progressLabel.setText(text.equals("") ? " " : text);
									progressBar.setMaximum(max);
									progressBar.setValue(0);
								}
							});
						}
						
						@Override
						public void set(final int value) {
							SwingUtilities.invokeLater(new Runnable() {
								public void run() {
									progressBar.setValue(value);
								}
							});
						}
					};
					m.run();
				} catch(Exception e) {
					String s = getStackTraceMessage(e);
					
					if(!new File(confDir, side.mcpside.srg_name).exists()) {
						s = side.mcpside.srg_name+" not found in conf directory. \n";
						switch(side) {
						case Client:
						case Server:
							s += "If you're using Forge, set the side to Universal (1.4.6+) or Universal_old (1.4.5 and earlier)";
							break;
						case Universal:
							s += "If you're not using Forge, set the side to Client or Server.\n";
							s += "If you're using Forge on 1.4.5 or earlier, set the side to Universal_old.";
							break;
						case Universal_old:
							s += "If you're not using Forge, set the side to Client or Server.\n";
							break;
						}
					}
					
					System.err.println(s);
					
					final String errMsg = s;
					SwingUtilities.invokeLater(new Runnable() {
						@Override
						public void run() {
							Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(errMsg), null);
							JOptionPane.showMessageDialog(GuiMain.this, errMsg, "BON - Error", JOptionPane.ERROR_MESSAGE);
						}
					});
				} finally {
					SwingUtilities.invokeLater(new Runnable() {
						@Override
						public void run() {
							progressLabel.setText(" ");
							progressBar.setValue(0);
							
							JOptionPane.showMessageDialog(GuiMain.this, "Done!", "BON", JOptionPane.INFORMATION_MESSAGE);
						}
					});
				}
			}
		};
		
		curTask.start();
	}
	
	private static String getPrintableStackTrace(Throwable e, Set<StackTraceElement> stopAt) {
		String s = e.toString();
		int numPrinted = 0;
		for(StackTraceElement ste : e.getStackTrace())
		{
			boolean stopHere = false;
			if(stopAt.contains(ste) && numPrinted > 0)
				stopHere = true;
			else {
				s += "\n    at " + ste.toString();
				numPrinted++;
				if(ste.getClassName().startsWith("javax.swing."))
					stopHere = true;
			}
			
			if(stopHere) {
				int numHidden = e.getStackTrace().length - numPrinted;
				s += "\n    ... "+numHidden+" more";
				break;
			}
		}
		return s;
	}
	
	private static String getStackTraceMessage(Throwable e) {
		String s = "An error has occurred - give immibis this stack trace (which has been copied to the clipboard)\n";
		
		s += "\n" + getPrintableStackTrace(e, Collections.<StackTraceElement>emptySet());
		while(e.getCause() != null) {
			Set<StackTraceElement> stopAt = new HashSet<StackTraceElement>(Arrays.asList(e.getStackTrace()));
			e = e.getCause();
			s += "\nCaused by: "+getPrintableStackTrace(e, stopAt);
		}
		return s;
	}
	
	public GuiMain() {
		JPanel contentPane = new JPanel();
		contentPane.setLayout(new GridBagLayout());
		GridBagConstraints gbc;
		
		gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.insets = new Insets(5, 5, 5, 5);
		gbc.anchor = GridBagConstraints.LINE_END;
		contentPane.add(new JLabel("Input file"), gbc.clone());
		gbc.gridy = 1;
		contentPane.add(new JLabel("Output file"), gbc.clone());
		gbc.gridy = 2;
		contentPane.add(new JLabel("MCP folder"), gbc.clone());
		gbc.gridy = 3;
		contentPane.add(new JLabel("Side"), gbc.clone());
		gbc.gridy = 4;
		contentPane.add(new JLabel("Operation"), gbc.clone());
		
		JButton chooseInputButton = new JButton("Browse");
		JButton chooseOutputButton = new JButton("Browse");
		JButton chooseMCPButton = new JButton("Browse");
		
		goButton = new JButton("Go");
		
		inputField = new JTextField();
		outputField = new JTextField();
		mcpField = new JTextField();
		
		sideSelect = new JComboBox<Side>(Side.values());
		opSelect = new JComboBox<Operation>(Operation.values());
		
		progressBar = new JProgressBar(JProgressBar.HORIZONTAL, 0, 100);
		progressLabel = new JLabel(" ", SwingConstants.LEFT);
		
		inputField.setMinimumSize(new Dimension(100, 0));
		
		gbc.gridx = 2;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.fill = GridBagConstraints.BOTH;
		contentPane.add(chooseInputButton, gbc.clone());
		gbc.gridy = 1;
		contentPane.add(chooseOutputButton, gbc.clone());
		gbc.gridy = 2;
		contentPane.add(chooseMCPButton, gbc.clone());
		
		gbc.gridx = 1;
		gbc.gridy = 0;
		gbc.ipadx = 200;
		contentPane.add(inputField, gbc.clone());
		gbc.gridy = 1;
		contentPane.add(outputField, gbc.clone());
		gbc.gridy = 2;
		contentPane.add(mcpField, gbc.clone());
		gbc.gridy = 3;
		contentPane.add(sideSelect, gbc.clone());
		gbc.gridy = 4;
		contentPane.add(opSelect, gbc.clone());
		
		gbc.gridx = 0;
		gbc.gridy = 5;
		gbc.gridwidth = 3;
		contentPane.add(goButton, gbc.clone());
		gbc.gridy = 6;
		contentPane.add(progressBar, gbc.clone());
		
		gbc.gridy = 7;
		contentPane.add(progressLabel, gbc.clone());
		
		setContentPane(contentPane);
		pack();
		
		browseDir.val = new File(prefs.get(PREFS_KEY_BROWSEDIR, "."));
		
		{
			String mcpDirString = prefs.get(PREFS_KEY_MCPDIR, ".");
			mcpField.setText(mcpDirString);
			
			if(!mcpDirString.equals(""))
				mcpBrowseDir.val = new File(mcpDirString);
			else
				mcpBrowseDir.val = new File(".");
		}
		
		chooseInputButton.addActionListener(new BrowseActionListener(inputField, true, this, false, browseDir));
		chooseOutputButton.addActionListener(new BrowseActionListener(outputField, false, this, false, browseDir));
		chooseMCPButton.addActionListener(new BrowseActionListener(mcpField, true, this, true, mcpBrowseDir));
		
		goButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				goButtonPressed();
			}
		});
		
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				savePrefs();
			}
		});
		
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setTitle("Bearded Octo Nemesis");
	}
	
	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				new GuiMain().setVisible(true);
			}
		});
	}
}
