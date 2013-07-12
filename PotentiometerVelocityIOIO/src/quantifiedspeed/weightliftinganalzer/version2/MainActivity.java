package quantifiedspeed.weightliftinganalzer.version2;


import ioio.lib.api.AnalogInput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections.buffer.CircularFifoBuffer;

import quantifiedspeed.weightliftinganalzer.versioN3.R;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This is the main activity of the HelloIOIO example application.
 * 
 * It displays a toggle button on the screen, which enables control of the
 * on-board LED. This example shows a very simple usage of the IOIO, by using
 * the {@link IOIOActivity} class. For a more advanced use case, see the
 * HelloIOIOPower example.
 */
public class MainActivity extends IOIOActivity {
	protected TextView displacementTextView_;
	protected TextView velocityTextView_;
	protected TextView repetitionTextView_;
	public static final String TAG = MainActivity.class.getSimpleName();
	
	// Velocity Calculation Data
	public static final int DATA_PERSISTENCE_PERIOD = 1000*60*5; // 1 second (1000ms) * 60seconds/minute * # of minutes of data to persist
	public static final int READING_INTERVAL = 100;
	public static final int DISPLACEMENT_DATA_POINT_COUNT = DATA_PERSISTENCE_PERIOD / READING_INTERVAL;	
	protected float[] displacements = new float[DISPLACEMENT_DATA_POINT_COUNT];
	protected int displacementsIndex = 0;
	protected float[] velocities = new float[DISPLACEMENT_DATA_POINT_COUNT];
	
	// Rep Detection Data
	public static final float REP_DETECTION_START_VELOCITY = 0.1f; // Will need tweaking
	public static final float REP_DETECTION_END_VELOCITY = 0.02f; // Will need tweaking
	public static final float REP_SPEED_CUTOFF = 1.5f; // Will need tweaking
	protected boolean inRepetition = false;
	protected float lastRepMaxVelocityVolts = 0;
	
	// Voltage To Distance Data
	public static final float METERS_PER_VOLT = 1; // Seems to be correct, should investigate further to get a more precise number
	
	/**
	 * Called when the activity is first created. Here we normally initialize
	 * our GUI.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		displacementTextView_ = (TextView) findViewById(R.id.displacement);
		velocityTextView_ = (TextView) findViewById(R.id.velocity);
		repetitionTextView_ = (TextView) findViewById(R.id.repetition);
	}

	/**
	 * This is the thread on which all the IOIO activity happens. It will be run
	 * every time the application is resumed and aborted when it is paused. The
	 * method setup() will be called right after a connection with the IOIO has
	 * been established (which might happen several times!). Then, loop() will
	 * be called repetitively until the IOIO gets disconnected.
	 */
	class Looper extends BaseIOIOLooper {
		/** The on-board LED. */
		private AnalogInput potIn_;
		public static final int POT_IN_PIN = 46;
		/**
		 * Called every time a connection with IOIO has been established.
		 * Typically used to open pins.
		 * 
		 * @throws ConnectionLostException
		 *             When IOIO connection is lost.
		 * 
		 * @see ioio.lib.util.AbstractIOIOActivity.IOIOThread#setup()
		 */
		@Override
		protected void setup() throws ConnectionLostException {
			potIn_ = ioio_.openAnalogInput(POT_IN_PIN);
		}

		/**
		 * Called repetitively while the IOIO is connected.
		 * 
		 * @throws ConnectionLostException
		 *             When IOIO connection is lost.
		 * 
		 * @see ioio.lib.util.AbstractIOIOActivity.IOIOThread#loop()
		 */
		@Override
		public void loop() throws ConnectionLostException {
			try {
				float potValue;
				//potValue = potIn_.read(); // Value from 0 to 1 of permitted voltage range
				potValue = potIn_.getVoltage(); // Actual voltage read
				displacements[displacementsIndex++] = potValue; 
				displacementsIndex %= DISPLACEMENT_DATA_POINT_COUNT;
				int velocitiesCalulationIndex = wrap_index(displacementsIndex - 2); //Move two back so we have a forward data point
				float velocity = calculateVelocity(displacements, velocitiesCalulationIndex);
				velocities[velocitiesCalulationIndex] = velocity;
				// Deals with detecting reps and keeping track of required data, returns true if RepTextField must be updated
				boolean repEnded = handleRepetition(velocity);
				// Updates UI to display displacement, velocity and rep info
				updatePotentiometerField(repEnded);
				Thread.sleep(READING_INTERVAL);
			} catch (InterruptedException e) {
				logException(e);
			}
		}
	}

	/**
	 * A method to create our IOIO thread.
	 * 
	 * @see ioio.lib.util.AbstractIOIOActivity#createIOIOThread()
	 */
	@Override
	protected IOIOLooper createIOIOLooper() {
		return new Looper();
	}
	
	protected Float convertVoltToMeter(Float voltage) {
		return voltage * METERS_PER_VOLT;
	}
	
	protected void updatePotentiometerField(final boolean updateRepTextField) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Float goodDisplacementVolts = displacements[wrap_index(displacementsIndex-2)];
				Float goodVelocityVolts = velocities[wrap_index(displacementsIndex-2)];
				if(goodDisplacementVolts != null) {
					Float goodDisplacementMeters = convertVoltToMeter(goodDisplacementVolts);
					displacementTextView_.setText("Displacement: " + String.format("%.3f", goodDisplacementMeters) + " meters");
				}else {
					displacementTextView_.setText("Displacement: null meters");
				}
				if(goodVelocityVolts != null) {
					Float goodVelocityMeters = convertVoltToMeter(goodVelocityVolts);
					velocityTextView_.setText("Velocity: " + String.format("%.3f", goodVelocityMeters) + " m/sec");
				}else {
					velocityTextView_.setText("Velocity: null m/sec");
				}
				if(updateRepTextField) {
					Float lastRepMaxVelocityMeters = convertVoltToMeter(lastRepMaxVelocityVolts);
					repetitionTextView_.setText(String.format("%.3f", lastRepMaxVelocityMeters) + " m/sec");
					if(lastRepMaxVelocityVolts < REP_SPEED_CUTOFF) {
						repetitionTextView_.setTextColor(Color.RED);
					} else {
						repetitionTextView_.setTextColor(Color.GREEN);
					}
					Toast.makeText(getApplicationContext(), "New Rep Detected", Toast.LENGTH_LONG).show();
				}
			}
		});
	}
	
	protected Float calculateVelocity(float[] displacements, int displacementsIndex) {
		// Two point formula used (f(x+h) - f(x-h))/(2*h)
		// Should h just be READING_INTERVAL and use constants 1 instead of scaling later???
		Float velocity = null;
		final int h = 1; // h is actually READING_INTERVAL, not 1 so we scale it in the velocity calculation below
		Float xPlusH = displacements[wrap_index(displacementsIndex + h)];
		Float xMinusH = displacements[wrap_index(displacementsIndex - h)];
		if(xPlusH != null && xMinusH != null) {
			velocity = (xPlusH - xMinusH) / (2	*h*(READING_INTERVAL/1000.0f)); //adjust h by the scale
		}
		return velocity;
	}
	
	protected int wrap_index(int unwrapped_index) {
		if(unwrapped_index < 0) {
			return DISPLACEMENT_DATA_POINT_COUNT + unwrapped_index;
		}else if(unwrapped_index >= DISPLACEMENT_DATA_POINT_COUNT) {
			return unwrapped_index % DISPLACEMENT_DATA_POINT_COUNT;
		}else {
			return unwrapped_index;
		}
	}
	
	protected void logException(Exception e) {
		Log.e(TAG, "Exception caught!", e);
	}

	// Deals with detecting reps and keeping track of required data 
	// Returns true to signify time to update RepTextView, false to not update RepTextView
	private boolean handleRepetition(float velocity) {
		// Handle start of repetition
		if(!inRepetition && velocity > REP_DETECTION_START_VELOCITY) {
			inRepetition = true;
			lastRepMaxVelocityVolts = velocity;
		}
		
		if(inRepetition) {
			// Handle updating data we keep track of
			if(velocity > lastRepMaxVelocityVolts) {
				lastRepMaxVelocityVolts = velocity;
			}
			
			// Handle ending rep and updating textfield
			if(velocity < REP_DETECTION_END_VELOCITY) {
				inRepetition = false;
				// Signify to update RepTextView
				return true;
			}
		}
		//Signify not to update RepTextView
		return false;
	}
}