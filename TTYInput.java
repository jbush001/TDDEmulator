import javax.sound.sampled.*;


class TTYInput implements Runnable
{
	static final int kBufferSize = 4096;
	static final double kMarkFrequency = 1400f;
	static final double kSpaceFrequency = 1800f;
	static final int kBitsPerSecond = 45;
	static final float kSampleRate = 8000f;
	static final int kBitsPerCharacter = 5;
	static final int kSamplesPerBit = (int) (kSampleRate / kBitsPerSecond);

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
	
	void setIgnoreInput(boolean ignore)
	{
		fIgnoreInput = ignore;
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
		byte[] data = new byte[kBufferSize];
		boolean waitForStartBit = true;
		int currentWord = 0;
		int bitCount = 0;
		int bitDurationCounter = 0;

		fAudioSource.start();
		while (true)
		{
			int got = fAudioSource.read(data, 0, data.length);
			for (int i = 0; i < got; i += 2)
			{
				double sample = (double) ((data[i] << 8) | data[i + 1]) / 32767;
				double markValue = fMarkBPF.processSample(sample);
				double spaceValue = fSpaceBPF.processSample(sample);
				double markLevel = fMarkLPF.processSample(Math.abs(markValue));
				double spaceLevel = fSpaceLPF.processSample(Math.abs(spaceValue));
				double nrzValue = markLevel - spaceLevel;
				if (fIgnoreInput)
				{
					// This is set when we are transmitting so we don't decode
					// our own signal.
					waitForStartBit = true;
					continue;
				}

				boolean isMark = nrzValue > 0;
			
				if (waitForStartBit)
				{
					if (Math.abs(nrzValue) > 0.1 && !isMark)
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

	private IIRFilter fMarkBPF = IIRFilter.makeBandpassFilter(kMarkFrequency, 
		50.0, kSampleRate);
	private IIRFilter fSpaceBPF = IIRFilter.makeBandpassFilter(kSpaceFrequency, 
		50.0, kSampleRate);
	private IIRFilter fMarkLPF = IIRFilter.makeLowPassFilter(kBitsPerSecond,
		kSampleRate);
	private IIRFilter fSpaceLPF = IIRFilter.makeLowPassFilter(kBitsPerSecond,
		kSampleRate);
	
	// Baudot to unicode tables
	char[] kLtrsTable = {
		' ', 'E', '\n', 'A', ' ', 'S', 'I', 'U', 
		'\r', 'D', 'R', 'J', 'N', 'F', 'C', 'K', 
		'T', 'Z', 'L', 'W', 'H', 'Y', 'P', 'Q', 
		'O', 'B', 'G', ' ', 'M', 'X', 'V', ' '
	};

	char[] kFigsTable = {
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
}
