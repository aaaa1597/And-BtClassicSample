package com.test.bluetoothclassicsample;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {
	private final static int	REQUEST_PERMISSIONS			= 1111;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		/* Bluetoothのサポート状況チェック 未サポート端末なら起動しない */
		if( !getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
			ErrDialog.create(MainActivity.this, "Bluetoothが、未サポートの端末です。\n終了します。").show();

		/* Bluetooth権限が許可されていない場合はリクエスト. */
		if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
				requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_PERMISSIONS);
			else
				requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSIONS);
		}

		/* Bluetooth ON/OFF判定 */
		final BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
		BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
		/* Bluetooth未サポート判定 未サポートならエラーpopupで終了 */
		if (bluetoothAdapter == null)
			ErrDialog.create(MainActivity.this, "Bluetooth未サポートの端末です。\nアプリを終了します。").show();
			/* OFFならONにするようにリクエスト */
		else if( !bluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			ActivityResultLauncher<Intent> startForResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
					result -> {
						if(result.getResultCode() != Activity.RESULT_OK) {
							ErrDialog.create(MainActivity.this, "BluetoothがOFFです。ONにして操作してください。\n終了します。").show();
						}
//						else {
//							bindUwsService();
//							bindUwsService();
//							bindUwsService();
//						}
					});
			startForResult.launch(enableBtIntent);
		}

		findViewById(R.id.btnConnect).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Intent intent = new Intent(MainActivity.this, DeviceListActivity.class);
				activityResultLauncher.launch(intent);
			}
		});
		findViewById(R.id.btnSend).setEnabled(false);

	}

	private final ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
			result -> {
				if(result.getResultCode() == Activity.RESULT_OK) {
					if(result.getData() != null) {
						//結果を受け取った後の処理
						TLog.d("ret = {0}", result.getData());
					}
				}
			});
}