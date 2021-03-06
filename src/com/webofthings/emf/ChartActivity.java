package com.webofthings.emf;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;

import com.webofthings.emf.arduino.ArduinoEMFAppLink;
import com.webofthings.emf.arduino.ArduinoLinkSimulator;
import com.webofthings.emf.experiments.ExperimentActivity;
import com.webofthings.emf.utils.Params;

/**
 * This is the activity responsible for displaying the measurements made by both sensors.
 * @author <a href="http://www.guinard.org">Dominique Guinard</a>
 */
public class ChartActivity extends Activity {
	private static final double INITIAL_X_MAX = 50.0D;
	private static final double INITIAL_Y_MAX = 10.0D;
	private static final int DATA_SET_MAX_SIZE = 500;

	private GraphicalView mChartView;
	private ArduinoEMFAppLink arduino;
	private Handler callBack;
	private XYSeries series;
	private double xTick = 0.0D;
	private double lastMinX = 0.0D;
	private XYMultipleSeriesRenderer renderer;
	private Params params;
	private int lineColor;
	private int surfaceColor;
	private Vibrator vibrator;
	private MenuItem startStopMnu;
	private MenuItem vibrateMnu;
	private boolean sensingActive;
	private boolean vibratorActive;
	private DataPersistantLogger dataLog;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.chart);

		vibrator = (Vibrator) getSystemService("vibrator");
		vibratorActive = true;

		dataLog = new DataPersistantLogger();

		params = new Params(getIntent(), this);
		showIntroduction();
	}

	protected void onResume() {
		super.onResume();
		sensingActive = false;

		params = new Params(getIntent(), this);

		if (params.isLF()) {
			lineColor = Color.parseColor("#f9b200");
			surfaceColor = Color.parseColor("#b5123c");
		} else {
			lineColor = Color.parseColor("#b5123c");
			surfaceColor = Color.parseColor("#f9b200");
		}

		if (mChartView == null) {
			LinearLayout layout = (LinearLayout) findViewById(R.id.chartLayout);
			mChartView = ChartFactory.getLineChartView(this, getDataSet(),
					getRenderer());
			layout.addView(mChartView, new LayoutParams(
					LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		} else {
			mChartView.repaint();
		}

		callBack = new Handler() {
			public void handleMessage(Message msg) {
				double y = Double.parseDouble((String) msg.obj);
				if (mChartView != null) {
					ChartActivity.this.refreshChart(y);
					ChartActivity.this.hapticFeedback(y);

					if (Params.LOG_MODE) {
						if (params.isLF()) {
							dataLog.storeLFDataDetailed(y);
						} else {
							dataLog.storeHFDataDetailed(y);
						}
					}
				}
			}
		};
		if (Params.SIMULATION_MODE)
			arduino = new ArduinoLinkSimulator(4568, callBack, 20, 2);
		else {
			arduino = new ArduinoEMFAppLink(4568, callBack);
		}
		arduino.startLink();
	}

	private void hapticFeedback(double value) {
		if (vibratorActive)
			if (params.isLF()) {
				vibrator.vibrate((long) value * 10);
			} else {
				vibrator.vibrate((long) value / 5);
			}
	}

	protected void onPause() {
		super.onPause();
		stopSensing();
		arduino.stopLink();
	}

	protected void onStop() {
		super.onStop();
		stopSensing();
		arduino.stopLink();
	}

	private void refreshChart(double lastValue) {
		if (series.getItemCount() > DATA_SET_MAX_SIZE) {
			series.clear();
			getRenderer().setXAxisMax(INITIAL_X_MAX);
			getRenderer().setXAxisMin(0.0D);
			lastMinX = 0.0D;
			xTick = 0.0D;
		}

		if (xTick > getRenderer().getXAxisMax()) {
			getRenderer().setXAxisMax(xTick);
			getRenderer().setXAxisMin(++lastMinX);
		}

		if (lastValue > getRenderer().getYAxisMax()) {
			getRenderer().setYAxisMax(lastValue);
		}
		series.add(xTick++, lastValue);

		mChartView.repaint();
	}

	private XYMultipleSeriesDataset getDataSet() {
		XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
		series = new XYSeries(getResources().getString(R.string.field));
		dataset.addSeries(series);
		return dataset;
	}

	private XYMultipleSeriesRenderer getRenderer() {
		if (renderer == null) {
			renderer = new XYMultipleSeriesRenderer();
			renderer.setAxisTitleTextSize(16);
			renderer.setChartTitleTextSize(20);
			renderer.setLabelsTextSize(15);
			renderer.setLegendTextSize(15);
			renderer.setPointSize(2);
			renderer.setMargins(new int[] { 10, 15, 10, 0 });
			renderer.setAxesColor(Color.DKGRAY);
			renderer.setLabelsColor(Color.LTGRAY);
			renderer.setXAxisMin(0);
			renderer.setXAxisMax(INITIAL_X_MAX);
			renderer.setYAxisMin(0);
			renderer.setYAxisMax(INITIAL_Y_MAX);
			renderer.setPanEnabled(true, true);

			XYSeriesRenderer r = new XYSeriesRenderer();
			r.setPointStyle(PointStyle.CIRCLE);
			r.setColor(lineColor);
			r.setFillPoints(true);
			r.setFillBelowLine(true);
			r.setFillBelowLineColor(surfaceColor);

			renderer.addSeriesRenderer(r);
		}
		return renderer;
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuItem showExpMnu = menu.add(0, 0, 0,
				getString(R.string.show_experiment));
		showExpMnu.setIcon(R.drawable.exp_small);

		MenuItem showExpConclMnu = menu.add(0, 1, 0,
				getString(R.string.show_experiment_conclusion));
		showExpConclMnu.setIcon(R.drawable.conclusion);

		MenuItem showMoreInfoMnu = menu.add(0, 2, 0,
				getString(R.string.show_more_info));
		showMoreInfoMnu.setIcon(R.drawable.info);

		startStopMnu = menu.add(0, 3, 0, getString(R.string.start_experiment));
		startStopMnu.setIcon(R.drawable.record);

		vibrateMnu = menu.add(0, 4, 0, getString(R.string.vibrator));
		vibrateMnu.setIcon(R.drawable.vibrator_off);
		return true;
	}

	private void showIntroduction() {
		Intent intent1 = new Intent(this, ExperimentActivity.class);
		intent1.putExtras(Params.prepareExtras(params.getFrequency(),
				params.getDeviceId(), false));
		startActivity(intent1);
	}

	private void showConclusion() {
		Intent intent = new Intent(this, ExperimentActivity.class);
		intent.putExtras(Params.prepareExtras(params.getFrequency(),
				params.getDeviceId(), true));
		startActivity(intent);
	}

	private void showMoreInfo() {
		Intent intent = new Intent(this, MoreInfoDeviceActivity.class);
		intent.putExtras(Params.prepareMoreInfoExtras(params.getMoreInfo()));
		startActivity(intent);
	}

	private void startSensing() {
		arduino.activateSensor(params.getFrequency());
		sensingActive = true;

		if (startStopMnu != null) {
			startStopMnu.setIcon(R.drawable.stop);
			startStopMnu.setTitle(getString(R.string.stop_experiment));
		}
	}

	private void stopSensing() {
		arduino.stopSensing();
		sensingActive = false;

		if (startStopMnu != null) {
			startStopMnu.setIcon(R.drawable.record);
			startStopMnu.setTitle(getString(R.string.start_experiment));
		}
	}

	private void toggleSensing() {
		if (sensingActive)
			stopSensing();
		else
			startSensing();
	}

	private void toggleVibrator() {
		if (vibratorActive) {
			vibrateMnu.setIcon(R.drawable.vibrator_on);
			vibratorActive = false;
			vibrator.cancel();
		} else {
			vibrateMnu.setIcon(R.drawable.vibrator_off);
			vibratorActive = true;
		}
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case 0:
			showIntroduction();
			return true;
		case 1:
			showConclusion();
			return true;
		case 2:
			showMoreInfo();
			return true;
		case 3:
			toggleSensing();
			return true;
		case 4:
			toggleVibrator();
			return true;
		}
		return false;
	}

}