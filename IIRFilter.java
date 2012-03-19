//
// Infinite Impulse Response filter, with a variable number of poles
//

class IIRFilter 
{
	static IIRFilter makeBandpassFilter(double centerFrequency, double bandwidth,
		double sampleRate)
	{
		double normFreq = 2 * Math.PI * (centerFrequency / sampleRate);
		double normBandwidth = bandwidth / sampleRate;
		double r = 1.0 - 3.0 * normBandwidth;
		double k = (1.0 - 2.0 * r * Math.cos(normFreq) + Math.pow(r, 2.0)) 
			/ (2.0 - 2.0 * Math.cos(normFreq));
		
		double[] aValues = new double[3];
		double[] bValues = new double[2];
		aValues[0] = 1.0 - 3.0 * normBandwidth;
		aValues[1] = 2.0 * (k - r) * Math.cos(normFreq);
		aValues[2] = Math.pow(r, 2.0) - k;
		bValues[0] = 2.0 * r * Math.cos(normFreq);
		bValues[1] = -Math.pow(r, 2.0);
		
		return new IIRFilter(aValues, bValues);
	}
	
	static IIRFilter makeLowPassFilter(double cutoffFrequency, double sampleRate)
	{
		double x = Math.pow(Math.E, -2 * Math.E * cutoffFrequency / sampleRate);
	
		double[] aValues = new double[1];
		double[] bValues = new double[1];
		aValues[0] = 1.0 - x;
		bValues[0] = x;

		return new IIRFilter(aValues, bValues);
	}

	protected IIRFilter(double[] aValues, double[] bValues)
	{
		fA = aValues;
		fB = bValues;
		fX = new double[aValues.length];
		fY = new double[bValues.length];
	}

	double processSample(double sample)
	{
		for (int i = fX.length - 1; i > 0; i--)
			fX[i] = fX[i - 1];
		
		fX[0] = sample;

		double y = 0.0;
		for (int i = 0; i < fX.length; i++)
			y += fA[i] * fX[i];
			
		for (int i = 0; i < fY.length; i++)
			y += fB[i] * fY[i];
			
		for (int i = fY.length - 1; i > 0; i--)
			fY[i] = fY[i - 1];
			
		fY[0] = y;

		return y;
	}

	double[] fX;
	double[] fY;
	double[] fA;
	double[] fB;
}
