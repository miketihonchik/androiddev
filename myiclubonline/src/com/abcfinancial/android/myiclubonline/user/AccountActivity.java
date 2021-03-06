package com.abcfinancial.android.myiclubonline.user;

import com.abcfinancial.android.myiclubonline.R;
import com.abcfinancial.android.myiclubonline.user.agreement.AgreementActivity;
import com.abcfinancial.android.myiclubonline.user.checkin.CheckInHistoryDatePickerActivity;
import com.abcfinancial.android.myiclubonline.usergroups.AccountGroupActivity;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class AccountActivity extends ListActivity {
	static final String[] ACTIONS = new String[] { "Agreement", "Billing",
			"Check-In" };

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setListAdapter(new ArrayAdapter<String>(this,
				R.layout.tab_activity_account, ACTIONS));

		ListView listView = getListView();
		listView.setTextFilterEnabled(true);

		listView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				if (position == 0) {
					Intent intent = new Intent(getParent(), AgreementActivity.class);
					AccountGroupActivity parentActivity = (AccountGroupActivity) getParent();
					parentActivity.startChildActivity("Agreement", intent);
				}
				if (position == 1) {
					Intent intent = new Intent(getApplicationContext(),
							CheckInHistoryDatePickerActivity.class);
					startActivity(intent);
				}
				if (position == 2) {
					Intent intent = new Intent(getParent(), CheckInHistoryDatePickerActivity.class);
					AccountGroupActivity parentActivity = (AccountGroupActivity) getParent();
					parentActivity.startChildActivity("CheckIn", intent);
				}
			}
		});
	}
}
