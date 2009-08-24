package com.NotiMe;

import java.io.IOException;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.StringTokenizer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import com.Utils.GMap;
import com.Utils.GoogleCalendarP;
import com.Utils.JavaCalendarUtils;
import com.Utils.NotiCalendar;
import com.Utils.NotiEvent;

/**
 * This is an example of service that will update its status bar balloon every 5
 * seconds for a minute.
 * 
 */
public class NotifyingService extends Service implements LocationListener {
	private final static String CLASS_TAG = "NotifyingService: ";
	static HashMap<String, NotiDetails> eventsDetails = new HashMap<String, NotiDetails>();

	static NotificationManager nNM;

	private static int notificationID = 1;

	static final int NOTIFY_ERR_LOCATION = 2;

	static final int NOTIFY_NO_ROUTE = 3;

	static final int NOTIFY_REG_TIME_PASSED = 1;
	static final int NOTIFY_REGULAR = 0;
	static final int NOTIFY_SNOOZE = 4;
	static final int NOTIFY_TIME_ALERT = 5;

	static int NOTIME_NOTIFICATIONS = 100;
	private static int reqCode = 0;

	static int getMinutesToGo(final Calendar getInCarTime) {
		final Calendar currentTime = Calendar.getInstance();
		return (int) JavaCalendarUtils.difference(currentTime, getInCarTime,
				JavaCalendarUtils.Unit.MINUTE);
	}

	NotiEvent firstEvent;

	GMap gMap = new GMap();

	private boolean isConnection = true;

	private boolean isProblemNotified = false;
	private double latitude, longitude;

	private LocationManager lm;

	private final Runnable mTask = new Runnable() {

		public void run() {

			// wait the 30 seconds.
			// here we should enter the logic of notification (time and place)
			while (run) {
				try {
					checkEvents();
				} catch (final Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if ((nMaxTimeCondition.block(3 * 10000))) {
					break;
				}

				// nCondition.close();

			}
			// code to stop the service!
			NotifyingService.this.stopSelf();
		}
	};

	// This is the object that receives interactions from clients. See
	// RemoteService for a more complete example.
	private final IBinder nBinder = new Binder() {
		@Override
		protected boolean onTransact(final int code, final Parcel data,
				final Parcel reply, final int flags) throws RemoteException {
			return super.onTransact(code, data, reply, flags);
		}
	};

	// private ConditionVariable nCondition;
	private ConditionVariable nMaxTimeCondition;

	private int notificationTime;

	// Start up the thread running the service. Note that we create a
	// separate thread because the service normally runs in the process's
	// main thread, which we don't want to block.
	final Thread notifyingThread = new Thread(null, mTask, "NotifyingService");

	LinkedList<NotiCalendar> parsedCalendarsList = null;
	LinkedList<NotiEvent> parsedEventsList = null;

	PreferenceManager pm = new PreferenceManager(PreferenceManager._activity);
	private boolean run = true;

	private void checkEvents() {
		final ConnectivityManager connec = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

		notificationTime = Integer.parseInt(pm.getNotificationTime());

		try {
			parsedCalendarsList = GoogleCalendarP.getAllCals();
		} catch (final Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		final String selectedCals = pm.getSelectedCalendarList();
		// splits it from the ,
		final StringTokenizer selectedIDsTokenizer = new StringTokenizer(
				selectedCals, ",");
		final LinkedList<String> cIDs = new LinkedList<String>();
		final int size = selectedIDsTokenizer.countTokens();
		// put each calendar in a separate string in cTest[] array
		for (int i = 0; i < size; i++) {
			cIDs.add(selectedIDsTokenizer.nextToken());
		}

		if (parsedCalendarsList != null) {
			parsedCalendarsList.retainAll(cIDs);

			try {
				parsedEventsList = GoogleCalendarP
						.getEvents(parsedCalendarsList);
			} catch (final Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			if ((parsedCalendarsList != null) && !parsedCalendarsList.isEmpty()) {
				try {
					firstEvent = parsedEventsList.getFirst();
				} catch (final Exception e) {
					// TODO: handle exception
				}
			}
		}

		if (firstEvent != null) {
			printEvent(0, null);
		}

		if (com.NotiMe.NotiMe.DEBUG_LOG) {
			Log.d(com.NotiMe.NotiMe.TAG, NotifyingService.CLASS_TAG
					+ "before regular check");
		}
		if (!(connec.getNetworkInfo(0).isConnectedOrConnecting() || connec
				.getNetworkInfo(1).isConnectedOrConnecting())
				|| ((latitude == 0) && (longitude == 0))) {
			isConnection = false;
			if (!isProblemNotified) {
				isProblemNotified = true;
				showShortNotification(getString(R.string.notifyingService_check));
			}
		} else if (firstEvent != null) {
			if (!NotifyingService.eventsDetails
					.containsKey(firstEvent.get_id())) {
				if (com.NotiMe.NotiMe.DEBUG_LOG) {
					Log.d(com.NotiMe.NotiMe.TAG, NotifyingService.CLASS_TAG
							+ "new event");
				}
				if (firstEvent.get_latitude() != null) {
					try {
						handleKnownLocation(firstEvent.get_latitude(),
								firstEvent.get_longitude(), null);
					} catch (final IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}

				} else {// event location is unclear
					if (com.NotiMe.NotiMe.DEBUG_LOG) {
						Log.d(com.NotiMe.NotiMe.TAG, NotifyingService.CLASS_TAG
								+ "event location is unclear");
					}
					final NotiDetails nd = new NotiDetails();
					nd.set_published(false);
					nd.set_origEvent(firstEvent);
					NotifyingService.eventsDetails.put(firstEvent.get_id(), nd);

					showNotification(firstEvent.get_id(), null,
							NotifyingService.NOTIFY_ERR_LOCATION);
				}

			} else {
				if (com.NotiMe.NotiMe.DEBUG_LOG) {
					Log.d(com.NotiMe.NotiMe.TAG, NotifyingService.CLASS_TAG
							+ "the event is not new");
				}

				updateDetEvent(firstEvent.get_id());
				final NotiDetails eventDet = NotifyingService.eventsDetails
						.get(firstEvent.get_id());

				if (eventDet.is_locationFixed() && !eventDet.is_dissmissed()
						&& !eventDet.is_published()) {
					if (com.NotiMe.NotiMe.DEBUG_LOG) {
						Log.d(com.NotiMe.NotiMe.TAG, NotifyingService.CLASS_TAG
								+ "only location fixed");
					}
					try {
						handleKnownLocation(Double.toString(eventDet
								.get_address().getLatitude()),
								Double.toString(eventDet.get_address()
										.getLongitude()), eventDet);
					} catch (final IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}

			}
		}

		if (com.NotiMe.NotiMe.DEBUG_LOG) {
			Log.d(com.NotiMe.NotiMe.TAG, NotifyingService.CLASS_TAG
					+ "before independent check");
		}
		final Collection<NotiDetails> e = NotifyingService.eventsDetails
				.values();
		for (final NotiDetails notiDetails : e) {
			updateDetEvent(notiDetails.get_origEvent().get_id());
			final NotiDetails eventDet = notiDetails;// eventsDetails.get(notiDetails.get_origEvent().get_id());

			if (com.NotiMe.NotiMe.DEBUG_LOG) {
				Log.d(com.NotiMe.NotiMe.TAG, NotifyingService.CLASS_TAG
						+ "is diss: " + eventDet.is_dissmissed()
						+ " is snooze: " + eventDet.is_snooze()
						+ " snooze publish: " + eventDet.is_snoozePublished()
						+ " snooze time: " + eventDet.get_snoozeTime()
						+ " is TA: " + eventDet.is_timeAlert() + "is TA Pub: "
						+ eventDet.is_timeAlertPublished());
			}
			final Calendar currentTime = Calendar.getInstance();
			final NotiEvent origEvent = eventDet.get_origEvent();
			if (eventDet.is_dissmissed()
					&& currentTime.before(origEvent.get_when())) {
				if (com.NotiMe.NotiMe.DEBUG_LOG) {
					Log.d(com.NotiMe.NotiMe.TAG, NotifyingService.CLASS_TAG
							+ "event removed from hashmap");
				}
				NotifyingService.eventsDetails.remove(origEvent.get_id());
			} else if (!eventDet.is_dissmissed() && eventDet.is_snooze()
					&& !eventDet.is_snoozePublished()) {// Snooze
				if (com.NotiMe.NotiMe.DEBUG_LOG) {
					Log.d(com.NotiMe.NotiMe.TAG, NotifyingService.CLASS_TAG
							+ "before snooze");
				}
				if (eventDet.get_snoozeTime().before(currentTime)) {
					if (com.NotiMe.NotiMe.DEBUG_LOG) {
						Log.d(com.NotiMe.NotiMe.TAG, NotifyingService.CLASS_TAG
								+ "in snooze");
					}
					eventDet.set_snoozePublished(true);
					try {
						handleSnoozeOrTimeAlert(eventDet);
					} catch (final IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
			} else if (!eventDet.is_dissmissed() && eventDet.is_timeAlert()
					&& !eventDet.is_timeAlertPublished()) {
				if (com.NotiMe.NotiMe.DEBUG_LOG) {
					Log.d(com.NotiMe.NotiMe.TAG, NotifyingService.CLASS_TAG
							+ "before time alert");
				}
				final Calendar alertTime = Calendar.getInstance();
				alertTime.setTime(eventDet.get_origEvent().get_when());
				alertTime.add(Calendar.MINUTE, (-1)
						* eventDet.get_timeAlertInMin());
				if (alertTime.before(currentTime)) {
					if (com.NotiMe.NotiMe.DEBUG_LOG) {
						Log.d(com.NotiMe.NotiMe.TAG, NotifyingService.CLASS_TAG
								+ "in time alert");
					}
					eventDet.set_timeAlertPublished(true);
					try {
						handleSnoozeOrTimeAlert(eventDet);
					} catch (final IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
			}
		}

	}

	private Integer getDrivingTimeInMin(final String eventLatitude,
			final String eventLongitude) throws IOException {
		return gMap.getTime(latitude + "," + longitude, eventLatitude + ","
				+ eventLongitude);
	}

	private Calendar getGetInCarTime(final NotiEvent event,
			final Integer drivingTimeInMin) {
		if (drivingTimeInMin == null) {
			return null;
		}
		final Calendar getInCarTime = Calendar.getInstance();
		getInCarTime.setTime(event.get_when());// event time
		getInCarTime.add(Calendar.MINUTE, drivingTimeInMin * (-1));// minus
		// driving
		// time

		return getInCarTime;
	}

	CharSequence getTimeText(final int time) {
		if (time > 0) {
			final int hours = Math.abs(time / 60);
			final int mins = time % 60;
			CharSequence h = null, m = null, t = null;
			if (hours > 1) {
				h = hours + getString(R.string.notifyingService_hours);
			} else if (hours == 1) {
				h = hours + getString(R.string.notifyingService_hour);
			}
			if (mins > 1) {
				m = mins + getString(R.string.notifyingService_minutes);
			} else if (mins == 1) {
				m = mins + getString(R.string.notifyingService_minute);
			}
			if ((m != null) && (h != null)) {
				t = h + getString(R.string.notifyingService_and) + m;
			} else if (m != null) {
				t = m;
			} else if (h != null) {
				t = h;
			}
			return t;
		} else {
			// return "notiMe can't locate your next appointment!";
			return getString(R.string.notifyingService_beenOnYourWay);
		}
	}

	private void handleKnownLocation(final String eventLatitude,
			final String eventLongitude, final NotiDetails eventDet)
			throws IOException {
		if (com.NotiMe.NotiMe.DEBUG_LOG) {
			Log.d(com.NotiMe.NotiMe.TAG, NotifyingService.CLASS_TAG
					+ "event location is known");
		}
		final Integer drivingTimeInMin = getDrivingTimeInMin(eventLatitude,
				eventLongitude);

		if ((drivingTimeInMin == null) || drivingTimeInMin.equals(-1)) {// no
			// route
			if ((eventDet != null) && !eventDet.is_noRoutePublished()) {
				eventDet.set_noRoutePublished(true);

				showNotification(eventDet.get_origEvent().get_id(), null,
						NotifyingService.NOTIFY_NO_ROUTE);

			} else if (eventDet == null) {
				final NotiDetails nd = new NotiDetails();
				nd.set_origEvent(firstEvent);
				nd.set_noRoutePublished(true);
				NotifyingService.eventsDetails.put(firstEvent.get_id(), nd);

				showNotification(firstEvent.get_id(), null,
						NotifyingService.NOTIFY_NO_ROUTE);
			}
			return;
		}

		final Calendar notificationPublishTime = Calendar.getInstance();

		if (drivingTimeInMin != null) {// there is a route
			if (com.NotiMe.NotiMe.DEBUG_LOG) {
				Log.d(com.NotiMe.NotiMe.TAG, NotifyingService.CLASS_TAG
						+ "there is a route");
			}
			final Calendar getInCarTime = getGetInCarTime(firstEvent,
					drivingTimeInMin);

			notificationPublishTime.setTime(firstEvent.get_when());// event time
			notificationPublishTime.add(Calendar.MINUTE, notificationTime
					* (-1));// minus notification time
			notificationPublishTime.add(Calendar.MINUTE, drivingTimeInMin
					* (-1));// minus driving time

			final Calendar currentTime = Calendar.getInstance();

			printEvent(drivingTimeInMin, getInCarTime);

			if (currentTime.after(notificationPublishTime)) {
				final NotiDetails nd = new NotiDetails();
				nd.set_published(true);
				nd.set_origEvent(firstEvent);

				nd.set_directionsURL("http://maps.google.com/maps?saddr="
						+ latitude + "," + longitude + "&daddr="
						+ eventLatitude + "," + eventLongitude + "");

				NotifyingService.eventsDetails.put(firstEvent.get_id(), nd);
				final int minToGo = NotifyingService
						.getMinutesToGo(getInCarTime);
				if (minToGo > 0) {
					showNotification(firstEvent.get_id(), getInCarTime,
							NotifyingService.NOTIFY_REGULAR);
				} else {
					showNotification(firstEvent.get_id(), getInCarTime,
							NotifyingService.NOTIFY_REG_TIME_PASSED);
				}

			}

		} else {// no route was found
			if (com.NotiMe.NotiMe.DEBUG_LOG) {
				Log.d(com.NotiMe.NotiMe.TAG, NotifyingService.CLASS_TAG
						+ "no route was found - todo?");
			}
		}
	}

	private void handleSnoozeOrTimeAlert(final NotiDetails eventDet)
			throws IOException {
		if (com.NotiMe.NotiMe.DEBUG_LOG) {
			Log.d(com.NotiMe.NotiMe.TAG, NotifyingService.CLASS_TAG
					+ "in handleSnoozeOrTimeAlert");
		}
		final NotiEvent origEvent = eventDet.get_origEvent();
		if (eventDet.get_notificationID() == 0) {
			eventDet.set_notificationID(NotifyingService.notificationID);
			NotifyingService.notificationID++;
			NotifyingService.eventsDetails.put(origEvent.get_id(), eventDet);
		}
		String eventLatitude, eventLongitude;
		if (eventDet.is_locationFixed()) {
			eventLatitude = Double.toString(eventDet.get_address()
					.getLatitude());
			eventLongitude = Double.toString(eventDet.get_address()
					.getLongitude());
		} else {
			eventLatitude = origEvent.get_latitude();
			eventLongitude = origEvent.get_longitude();
		}

		Calendar getInCarTime = null;
		try {
			getInCarTime = getGetInCarTime(origEvent, getDrivingTimeInMin(
					eventLatitude, eventLongitude));
		} catch (final Exception e) {
			// TODO: handle exception
		}

		if (eventDet.is_snooze()) {
			if (com.NotiMe.NotiMe.DEBUG_LOG) {
				Log.d(com.NotiMe.NotiMe.TAG, NotifyingService.CLASS_TAG
						+ "in handleSnoozeOrTimeAlert - snooze");
			}
			showNotification(origEvent.get_id(), getInCarTime,
					NotifyingService.NOTIFY_SNOOZE);
		} else {
			if (com.NotiMe.NotiMe.DEBUG_LOG) {
				Log.d(com.NotiMe.NotiMe.TAG, NotifyingService.CLASS_TAG
						+ "in handleSnoozeOrTimeAlert - time alert");
			}
			showNotification(origEvent.get_id(), getInCarTime,
					NotifyingService.NOTIFY_TIME_ALERT);
		}

	}

	private PendingIntent makeNotiMeIntent(final Calendar getInCarTime,
			final NotiDetails eventDet, final int notificationType) {
		// The PendingIntent to launch our activity if the user selects this
		// notification. Note the use of FLAG_UPDATE_CURRENT so that if there
		// is already an active matching pending intent, we will update its
		// extras to be the ones passed in here.

		// PendingIntent contentIntent = null;
		final String id = eventDet.get_origEvent().get_id();

		switch (notificationType) {
		case NOTIFY_REGULAR:
			return PendingIntent.getActivity(this, NotifyingService.reqCode++,
					new Intent(this, NotificationDisplay.class).setFlags(
							Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(
							"com.NotiMe.ID", id).putExtra(
							"com.NotiMe.GetInCarTime", getInCarTime).putExtra(
							"com.NotiMe.afterTimeAlert",
							eventDet.is_timeAlert()),
					PendingIntent.FLAG_UPDATE_CURRENT);
		case NOTIFY_REG_TIME_PASSED:
			return PendingIntent.getActivity(this, NotifyingService.reqCode++,
					new Intent(this, NotificationDisplay.class).setFlags(
							Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(
							"com.NotiMe.ID", id).putExtra(
							"com.NotiMe.GetInCarTime", getInCarTime).putExtra(
							"com.NotiMe.afterTimeAlert",
							eventDet.is_timeAlert()),
					PendingIntent.FLAG_UPDATE_CURRENT);
		case NOTIFY_ERR_LOCATION:
			return PendingIntent.getActivity(this, NotifyingService.reqCode++,
					new Intent(this, NotiErrDisplay.class).setFlags(
							Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(
							"com.NotiMe.ID", id).putExtra(
							"com.NotiMe.hideDots", false),
					PendingIntent.FLAG_UPDATE_CURRENT);
		case NOTIFY_NO_ROUTE:
			return PendingIntent.getActivity(this, NotifyingService.reqCode++,
					new Intent(this, NotiErrDisplay.class).setFlags(
							Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(
							"com.NotiMe.ID", id).putExtra(
							"com.NotiMe.hideDots", true),
					PendingIntent.FLAG_UPDATE_CURRENT);
		case NOTIFY_SNOOZE:
			return PendingIntent.getActivity(this, NotifyingService.reqCode++,
					new Intent(this, NotificationDisplay.class).setFlags(
							Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(
							"com.NotiMe.ID", id).putExtra(
							"com.NotiMe.GetInCarTime", getInCarTime).putExtra(
							"com.NotiMe.afterTimeAlert",
							eventDet.is_timeAlert()),
					PendingIntent.FLAG_UPDATE_CURRENT);
		case NOTIFY_TIME_ALERT:
			return PendingIntent.getActivity(this, NotifyingService.reqCode++,
					new Intent(this, NotificationDisplay.class).setFlags(
							Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(
							"com.NotiMe.ID", id).putExtra(
							"com.NotiMe.GetInCarTime", getInCarTime).putExtra(
							"com.NotiMe.afterTimeAlert",
							eventDet.is_timeAlert()),
					PendingIntent.FLAG_UPDATE_CURRENT);

		}
		return null;
	}

	@Override
	public IBinder onBind(final Intent intent) {
		return nBinder;
	}

	@Override
	public void onCreate() {

		pm.setRunning(true);

		setCalendars();

		NotifyingService.nNM = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		// nCondition = new ConditionVariable(false);
		nMaxTimeCondition = new ConditionVariable(false);
		notifyingThread.start();

		lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		final Location lastKnownLocation = lm
				.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		final Location lastKnownLocationNet = lm
				.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		if (lastKnownLocation != null) {
			latitude = lastKnownLocation.getLatitude();
			longitude = lastKnownLocation.getLongitude();
		} else if (lastKnownLocationNet != null) {
			latitude = lastKnownLocationNet.getLatitude();
			longitude = lastKnownLocationNet.getLongitude();
		}
		startListening();
	}

	@Override
	public void onDestroy() {
		pm.setRunning(false);
		if (com.NotiMe.NotiMe.DEBUG_LOG) {
			Log.d(com.NotiMe.NotiMe.TAG, NotifyingService.CLASS_TAG
					+ "in onDestroy");
		}
		// Cancel the persistent notification.
		NotifyingService.nNM.cancel(NotifyingService.NOTIME_NOTIFICATIONS);

		// cancel the others
		final Collection<NotiDetails> e = NotifyingService.eventsDetails
				.values();
		for (final NotiDetails notiDetails : e) {
			final NotiDetails eventDet = notiDetails;
			NotifyingService.nNM.cancel(eventDet.get_notificationID());
		}
		e.clear();

		// Stop the thread from generating further notifications
		run = false;

		stopListening();
		super.onDestroy();
	}

	/**********************************************************************
	 * LocationListener overrides below
	 **********************************************************************/
	// @Override
	public void onLocationChanged(final Location location) {
		// this code tricks the emulator to work...
		// stopListening();
		// startListening();

		latitude = location.getLatitude();
		longitude = location.getLongitude();

		// Toast.makeText(NotifyingService.this, s, Toast.LENGTH_LONG).show();
		if (com.NotiMe.NotiMe.DEBUG_LOG) {
			String s = "";
			s += "Time: " + location.getTime() + "\n";
			s += "\tLatitude:  " + location.getLatitude() + "\n";
			s += "\tLongitude: " + location.getLongitude() + "\n";
			s += "\tAccuracy:  " + location.getAccuracy() + "\n";

			Log.d(com.NotiMe.NotiMe.TAG, NotifyingService.CLASS_TAG
					+ "location changed: " + s);
		}

	}

	// @Override
	public void onProviderDisabled(final String provider) {
	}

	// @Override
	public void onProviderEnabled(final String provider) {
	}

	// @Override
	public void onStatusChanged(final String provider, final int status,
			final Bundle extras) {
	}

	private void printEvent(final int drivingTimeInMin,
			final Calendar getInCarTime) {// temp
		// -
		// for
		// debug

		if (com.NotiMe.NotiMe.DEBUG_LOG) {

			Log.d(com.NotiMe.NotiMe.TAG, NotifyingService.CLASS_TAG
					+ "print event:");

			final Calendar currentTime = Calendar.getInstance();

			Log.d(com.NotiMe.NotiMe.TAG, "Event Title: "
					+ firstEvent.get_title());
			Log.d(com.NotiMe.NotiMe.TAG, "Event Location: "
					+ firstEvent.get_where());
			if (getInCarTime != null) {
				Log.d(com.NotiMe.NotiMe.TAG, "Driving Time: hours: "
						+ Math.abs(drivingTimeInMin / 60) + " mins: "
						+ drivingTimeInMin % 60);
			}
			Log
					.d(com.NotiMe.NotiMe.TAG, "event time: "
							+ firstEvent.get_when());
			if (getInCarTime != null) {
				Log.d(com.NotiMe.NotiMe.TAG, "get in car time: "
						+ new Date(getInCarTime.getTimeInMillis()));
			}
			Log.d(com.NotiMe.NotiMe.TAG, "current time: "
					+ new Date(currentTime.getTimeInMillis()));
		}
	}

	private void setCalendars() {
		LinkedList<NotiCalendar> calendarList = null;
		try {
			calendarList = GoogleCalendarP.getAllCals();
		} catch (final Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		String cNames = "";
		String cIDs = "";

		// handle null!!
		for (final NotiCalendar notiCalendar2 : calendarList) {
			final NotiCalendar notiCalendar = notiCalendar2;
			cNames = cNames + notiCalendar.get_title() + ",";
			cIDs = cIDs + notiCalendar.get_id() + ",";
		}
		pm.setCalendarListNames(cNames);
		pm.setCalendarListIDs(cIDs);
	}

	private void showNotification(final String eventID,
			final Calendar getInCarTime, final int notificationType) {
		Log.d(com.NotiMe.NotiMe.TAG, NotifyingService.CLASS_TAG
				+ "in showNotification");
		int minToGo = 0;
		if (getInCarTime != null) {
			minToGo = NotifyingService.getMinutesToGo(getInCarTime);
		}
		// we'll use the same text for the ticker and the expanded notification
		CharSequence text = "";
		final NotiDetails eventDet = NotifyingService.eventsDetails
				.get(eventID);

		switch (notificationType) {
		case NOTIFY_REGULAR:
			text = eventDet.get_origEvent().get_title()
					+ getString(R.string.notifyingService_minus)
					+ getString(R.string.notifyingService_toGo)
					+ getTimeText(minToGo);

			break;
		case NOTIFY_REG_TIME_PASSED:
			text = getTimeText(minToGo);
			break;
		case NOTIFY_ERR_LOCATION:
			text = getString(R.string.notifyingService_error);
			break;
		case NOTIFY_NO_ROUTE:
			text = getString(R.string.notifyingService_noroute);
			break;
		case NOTIFY_SNOOZE:
			text = getString(R.string.notifyingService_snoozed)
					+ eventDet.get_origEvent().get_title();
			if (minToGo > 0) {
				text = text + getString(R.string.notifyingService_minus)
						+ getString(R.string.notifyingService_toGo)
						+ getTimeText(minToGo);

			}

			else if (isConnection) {
				text = text + " " + getTimeText(minToGo);
			}

			break;
		case NOTIFY_TIME_ALERT:
			text = getString(R.string.notifyingService_timealert)
					+ eventDet.get_origEvent().get_title()
					+ getString(R.string.notifyingService_startin)
					+ getTimeText(eventDet.get_timeAlertInMin());
			break;

		}

		// Set the icon, scrolling text and timestamp.
		// Note that we pass null for tickerText.
		final Notification notification = new Notification(R.drawable.noticon,
				text, System.currentTimeMillis());

		int effects = 0;
		if (pm.isVibrationNotification()) {
			effects |= Notification.DEFAULT_VIBRATE;
		}
		if (pm.isSoundNotification()) {
			// if (pr.getSoundURI() != "") {
			// notification.sound = Uri.parse(pr.getSoundURI());
			// } else {
			effects |= Notification.DEFAULT_SOUND;
			// }
		}
		if (pm.isLightNotification()) {
			// effects |= Notification.DEFAULT_LIGHTS;
			notification.flags = Notification.FLAG_SHOW_LIGHTS;
			notification.ledOnMS = 500;
			notification.ledOffMS = 500;
			notification.ledARGB = 123;
		}

		notification.defaults = effects;

		final PendingIntent contentIntent = makeNotiMeIntent(getInCarTime,
				eventDet, notificationType);

		// Set the info for the views that show in the notification panel.
		notification.setLatestEventInfo(this, getText(R.string.app_name), text,
				contentIntent);

		// Send the notification.
		// We use a layout id because it is a unique number. We use it later to
		// cancel.
		if (eventDet.get_notificationID() == 0) {
			NotifyingService.nNM.notify(NotifyingService.NOTIME_NOTIFICATIONS,
					notification);
		} else {
			NotifyingService.nNM.notify(eventDet.get_notificationID(),
					notification);
		}
	}

	private void showShortNotification(final String text) {
		Log.d(com.NotiMe.NotiMe.TAG, NotifyingService.CLASS_TAG
				+ "in showShortNotification");
		// Set the icon, scrolling text and timestamp.
		// Note that we pass null for tickerText.
		final Notification notification = new Notification(R.drawable.noticon,
				getText(R.string.notifyingService_problem), System
						.currentTimeMillis());

		int effects = 0;
		if (pm.isVibrationNotification()) {
			effects |= Notification.DEFAULT_VIBRATE;
		}
		if (pm.isSoundNotification()) {
			// if (pr.getSoundURI() != "") {
			// notification.sound = Uri.parse(pr.getSoundURI());
			// } else {
			effects |= Notification.DEFAULT_SOUND;
			// }
		}
		if (pm.isLightNotification()) {
			// effects |= Notification.DEFAULT_LIGHTS;
			notification.flags = Notification.FLAG_SHOW_LIGHTS;
			notification.ledOnMS = 500;
			notification.ledOffMS = 500;
			notification.ledARGB = 123;
		}

		notification.defaults = effects;

		notification.flags = Notification.FLAG_AUTO_CANCEL
				+ Notification.FLAG_ONLY_ALERT_ONCE;

		final PendingIntent contentIntent = PendingIntent.getActivity(this,
				NotifyingService.reqCode++, new Intent(this, NotiMe.class), 0);

		// Set the info for the views that show in the notification panel.
		notification.setLatestEventInfo(this, getText(R.string.app_name), text,
				contentIntent);

		NotifyingService.nNM.notify(NotifyingService.notificationID,
				notification);
		NotifyingService.notificationID++;

	}

	/**********************************************************************
	 * helpers for starting/stopping monitoring of GPS changes below
	 **********************************************************************/
	private void startListening() {

		final Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		criteria.setAltitudeRequired(true);
		criteria.setBearingRequired(false);
		criteria.setCostAllowed(true);
		criteria.setPowerRequirement(Criteria.POWER_LOW);

		// lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000// 1min
		// , 1000// 100m
		// , this);
		lm.requestLocationUpdates(lm.getBestProvider(criteria, true), 60000// 1min
				, 1000// 100m
				, this);
	}

	private void stopListening() {
		if (lm != null) {
			lm.removeUpdates(this);
		}
	}

	private void updateDetEvent(final String eventID) {
		if (parsedEventsList == null) {
			return;
		}
		final int index = parsedEventsList.indexOf(eventID);
		if (index != -1) {
			final NotiEvent updatedEvent = parsedEventsList.get(index);
			final NotiDetails eventDet = NotifyingService.eventsDetails
					.get(eventID);
			eventDet.set_origEvent(updatedEvent);
			NotifyingService.eventsDetails.put(eventID, eventDet);
		}
	}
}
