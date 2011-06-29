package sk.michalko.smsticket.handlers;

import java.text.SimpleDateFormat;
import java.util.Date;

import sk.michalko.smsticket.R;
import sk.michalko.smsticket.TicketDao;
import sk.michalko.smsticket.TicketState;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

/**
 * @author mmm
 * 
 * @description This class is base class to all notification callbacks used in
 *              the application. It purpose is to define common functionality
 *              for process of dealing with state change notifications.
 * 
 */
public class SMSReceiver extends BroadcastReceiver {

	static final String TAG = SMSReceiver.class.getSimpleName();

	SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm");

	Context ctx = null;

	@Override
	public void onReceive(Context context, Intent intent) {

		ctx = context;

		final String INTENT_SMS_SENT = ctx.getResources().getString(R.string.intent_sms_sent);
		final String INTENT_SMS_DELIVERED = ctx.getResources().getString(R.string.intent_sms_delivered);
		final String INTENT_SMS_RECEIVED = ctx.getResources().getString(R.string.intent_sms_received);


		// what are we notified about ?
		String action = intent.getAction();
		String ticketId = intent.getDataString();

		Log.d(TAG, "Received notification " + action + ", " + ticketId);

//		Toast.makeText(context, action, Toast.LENGTH_LONG).show();

		TicketDao ticket = null;

		if (INTENT_SMS_SENT.equalsIgnoreCase(action)) {

			ticket = TicketDao.getByUUID(ticketId, ctx);
			changeState(TicketState.TICKET_ORDER_CREATED, TicketState.TICKET_ORDER_IN_PROGRESS, ticket);
			ticket.update(ctx);
			Toast.makeText(context, ctx.getResources().getString(R.string.intent_sms_sent_toast), Toast.LENGTH_LONG).show();

		} else if (INTENT_SMS_DELIVERED.equalsIgnoreCase(action)) {

			ticket = TicketDao.getByUUID(ticketId, ctx);
			changeState(TicketState.TICKET_ORDER_IN_PROGRESS, TicketState.TICKET_ORDER_CONFIRMED, ticket);
			ticket.update(ctx);
			Toast.makeText(context, ctx.getResources().getString(R.string.intent_sms_delivered_toast), Toast.LENGTH_LONG).show();

		} else if (INTENT_SMS_RECEIVED.equalsIgnoreCase(action)) {

			// read received messages from intent object
			Bundle bundle = intent.getExtras();
			SmsMessage[] messages = null;
			String text = "";
			if (bundle != null) {
				Object[] pdus = (Object[]) bundle.get("pdus");
				messages = new SmsMessage[pdus.length];
				int j = 0;

				for (int i = 0; i < messages.length; i++) {
					messages[j] = SmsMessage.createFromPdu((byte[]) pdus[i]);
					text = messages[j].getMessageBody();
					// Detect SMS Ticket message
					//if ("DPB".regionMatches(0, text, 0, 3)) {
					if (text != null && text.contains("Prestupny CL")) {
						j++;
						//Log.d(TAG, "Found Ticket SMS: \n" + text + "\nClass:\n" + messages[j].getMessageClass() + "\nSubject: " +messages[j].getPseudoSubject());
					}
				}

				// Lets assume we have only one ticket here
				if (j == 1) {
					// Get last created ticket, but not validated
					ticket = TicketDao.getCurrent(ctx);
					ticket.setState(TicketState.TICKET_VALID.toString());
					ticket.setChanged(new Date());
					ticket.setSmsBody(messages[0].getMessageBody());
					try{
						String dateFrom = messages[0].getMessageBody().substring(70, 86);
						String dateThrough = dateFrom.substring(0,11) + messages[0].getMessageBody().substring(90, 95);
						ticket.setValidFrom(dateFormat.parse(dateFrom));
						ticket.setValidThrough(dateFormat.parse(dateThrough));
					} catch (Exception e) 
					{
						Log.e(TAG, "Message cannot be parsed for ticket." + e);
					}
					Toast.makeText(context, ctx.getResources().getString(R.string.intent_sms_received_toast), Toast.LENGTH_LONG).show();
					changeState(TicketState.TICKET_ORDER_CONFIRMED, TicketState.TICKET_VALID, ticket);
					StringBuffer message = new StringBuffer();
					message.append("Found Ticket SMS: \n");
					message.append(messages[0].getMessageBody());
					message.append("\nClass:\n");
					message.append(messages[0].getMessageClass());
					message.append("\nSubject: ");
					message.append(messages[0].getPseudoSubject());
					
					new LogAsyncTask().execute(message.toString());
					ticket.update(ctx);
				}
			}
		}
	}

	public void changeState(TicketState currentState, TicketState nextState, TicketDao ticket) {

		// if (TicketState.valueOf(ticket.getState()) == currentState) {
		ticket.setState(nextState.toString());

		Intent intentUpdate = new Intent();
		intentUpdate.setAction(ctx.getResources().getString(R.string.intent_update));
		ctx.sendBroadcast(intentUpdate);
		// } else Log.e(TAG,"Ticket in unexpected state " + ticket.getState() +
		// ", expected " + currentState);
	}

}
