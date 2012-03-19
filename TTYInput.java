import javax.sound.sampled.*;
import java.util.*;

class TTYInput implements Runnable
{
	static final int kBufferSize = 1600;
	static final double kMarkFrequency = 1400f;
	static final double kSpaceFrequency = 1800f;
	static final int kBitsPerSecond = 45;
	static final float kSampleRate = 8000f;
	static final int kBitsPerCharacter = 5;
	static final int kSamplesPerBit = (int) (kSampleRate / kBitsPerSecond);
	static final long kGuardDelay =  (long) (1000 * kBufferSize / kSampleRate * 2);

	public TTYInput()
	{
		try
		{
			AudioFormat format = new AudioFormat(kSampleRate, 16, 1, true, true);
			DataLine.Info info = new DataLine.Info(TargetDataLine.class, 
				format);
			if (!AudioSystem.isLineSupported(info))
			{
				System.out.println("Line matching " + info + " not supported.");
				return;
			}

			fAudioSource = (TargetDataLine) AudioSystem.getLine(info);
			fAudioSource.open(format, kBufferSize);
		}
		catch (LineUnavailableException exc)
		{
			System.out.println("TTYInput: unable to open line " + exc);
		}

		fThread = new Thread(this);
		fThread.start();
	}

	interface TTYInputListener
	{
		public void handleCode(char c);
	}
	
	void setListener(TTYInputListener handler)
	{
		fHandler = handler;
	}
	
	synchronized void setIgnoreInput(boolean ignore)
	{
		if (ignore)
		{
			if (fGuardTimer != null)
				fGuardTimer.cancel(); 

			fIgnoreInput = true;
		}
		else
		{
			// Some data may be in the current input buffer.  Wait 
			// a spell for it to empty out so we don't pick up pieces
			// of the last character.
			fGuardTimer = new Timer();
			fGuardTimer.schedule(new TimerTask() {
				public void run() {
					fIgnoreInput = false;
				}
			}, kGuardDelay);
		}
	}

	void processCode(char code)
	{
		if (code == 0x1b)
			fModeIsFigs = true;
		else if (code == 0x1f)
			fModeIsFigs = false;
		else 
		{
			char ch;
			if (fModeIsFigs)
				ch = kFigsTable[(int) code];
			else
				ch = kLtrsTable[(int) code];
				
			if (fHandler != null)
				fHandler.handleCode(ch);
		}
	}

	public void run()
	{
		IIRFilter markBPF = IIRFilter.makeBandpassFilter(kMarkFrequency, 
			50.0, kSampleRate);
		IIRFilter spaceBPF = IIRFilter.makeBandpassFilter(kSpaceFrequency, 
			50.0, kSampleRate);
		IIRFilter markLPF = IIRFilter.makeLowPassFilter(kBitsPerSecond,
			kSampleRate);
		IIRFilter spaceLPF = IIRFilter.makeLowPassFilter(kBitsPerSecond,
			kSampleRate);
		byte[] sampleBuffer = new byte[kBufferSize];
		boolean waitForStartBit = true;
		int currentWord = 0;
		int bitCount = 0;
		int bitDurationCounter = 0;

		fAudioSource.start();
		while (true)
		{
			int got = fAudioSource.read(sampleBuffer, 0, sampleBuffer.length);
			for (int i = 0; i < got; i += 2)
			{
				double sample = (double) ((sampleBuffer[i] << 8) | sampleBuffer[i + 1]) / 32767;
				double markValue = markBPF.processSample(sample);
				double spaceValue = spaceBPF.processSample(sample);
				double markLevel = markLPF.processSample(Math.abs(markValue));
				double spaceLevel = spaceLPF.processSample(Math.abs(spaceValue));
				double nrzValue = markLevel - spaceLevel;
				if (fIgnoreInput)
				{
					// Drop the entire buffer if we are ignoring input.
					waitForStartBit = true;
					break;
				}

				boolean isMark = nrzValue > 0;
				if (waitForStartBit)
				{
					// Set a threshold for the start bit so we don't trigger
					// due to noise when there is no signal.
					if (nrzValue < -0.1)
					{
						waitForStartBit = false;
						bitDurationCounter = (int)(kSamplesPerBit * 1.5);
						bitCount = 0;
						currentWord = 0;
					}
				}
				else
				{
					if (--bitDurationCounter == 0)
					{
						bitDurationCounter = kSamplesPerBit;
						if (++bitCount == 6)
						{
							if (isMark)	 // Valid stop bit
								processCode((char) currentWord);

							waitForStartBit = true;
						}
						else
							currentWord = (currentWord >> 1) | ((isMark ? 1 : 0) << 4);
					}
				}
			}
		}
	}
	
	// Baudot to unicode tables
	private static final char[] kLtrsTable = {
		' ', 'E', '\n', 'A', ' ', 'S', 'I', 'U', 
		'\r', 'D', 'R', 'J', 'N', 'F', 'C', 'K', 
		'T', 'Z', 'L', 'W', 'H', 'Y', 'P', 'Q', 
		'O', 'B', 'G', ' ', 'M', 'X', 'V', ' '
	};

	private static final char[] kFigsTable = {
		' ', '3', '\n', '-', ' ', '-', '8', '7', 
		'\r', '$', '4', '\'', ',', '!', ':', '(', 
		'5', '\"', ')', '2', '=', '6', '0', '1', 
		'9', '?', '+', ' ', '.', '/', ';', ' '
	};
		
	private boolean fModeIsFigs = false;
	private TargetDataLine fAudioSource;
	private Thread fThread;
	private TTYInputListener fHandler;
	private boolean fIgnoreInput = false;
	private Timer fGuardTimer;
}
