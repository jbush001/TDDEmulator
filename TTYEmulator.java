import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;

class TTYEmulator extends JPanel
{
	public TTYEmulator()
	{
		super(new BorderLayout());

		fConversationView = new JTextPane();
		fConversationView.setEditable(false);	
		StyledDocument doc = fConversationView.getStyledDocument();		
		Style def = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
		Style regular = doc.addStyle("regular", def);
        StyleConstants.setFontFamily(regular, "SansSerif");
		Style me = doc.addStyle("me", regular);
        StyleConstants.setBold(me, true);
        StyleConstants.setForeground(me, Color.blue);
		Style them = doc.addStyle("them", regular);
		
		JScrollPane conversationScroll = new JScrollPane(fConversationView);
		add(conversationScroll, BorderLayout.CENTER);

		fInputField = new JTextField();
		fInputField.addActionListener(new ActionListener() 
			{
				public void actionPerformed(ActionEvent e)
				{
					handleTextInput();
				}
			});
		add(fInputField, BorderLayout.SOUTH);
		setPreferredSize(new Dimension(400,300));

		fOutput = new TTYOutput();
		fInput = new TTYInput();
		
		fInput.setListener(new TTYInput.TTYInputListener() {
			public void handleCode(char ch) {
				addCode(ch);
			}
		});

		fOutput.setListener(new TTYOutput.TTYOutputListener() {
			public void ttyIsSending(boolean isSending) {
				// If we are actively sending, disable our receiver so 
				// we don't echo characters.
				fInput.setIgnoreInput(isSending);
			}
		});
	}

	void handleTextInput()
	{
		String input = fInputField.getText();
		fInputField.setText("");
		
		try
		{
			StyledDocument doc = fConversationView.getStyledDocument();
			doc.insertString(doc.getLength(), input + "\n", doc.getStyle("me"));
		}
		catch (Exception exc)
		{
			System.out.println(exc);
		}

		fOutput.enqueueString(input);
	}
	
	void addCode(char ch)
	{
		try
		{
			StyledDocument doc = fConversationView.getStyledDocument();
			doc.insertString(doc.getLength(), "" + ch, doc.getStyle("them"));
		}
		catch (Exception exc)
		{
			System.out.println(exc);
		}
	}

    private static void createAndShowGUI() 
    {
		//Create and set up the window.
		JFrame frame = new JFrame("TTY Emulator");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		//Add the ubiquitous "Hello World" label.
		TTYEmulator emulator = new TTYEmulator();
		frame.getContentPane().add(emulator);
		
		//Display the window.
		frame.pack();
		frame.setVisible(true);
    }

	public static void main(String[] args)
	{
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createAndShowGUI();
            }
        });	
	}
	
	private TTYOutput fOutput;
	private TTYInput fInput;
	private JTextPane fConversationView;
	private JTextField fInputField;
}
