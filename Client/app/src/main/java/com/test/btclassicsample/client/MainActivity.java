package com.test.btclassicsample.client;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public class MainActivity extends AppCompatActivity {
	private BluetoothAdapter	mBluetoothAdapter;
	private final static int	REQUEST_PERMISSIONS = 1111;

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
//		final BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
//		mBluetoothAdapter = bluetoothManager.getAdapter();
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		/* Bluetooth未サポート判定 未サポートならエラーpopupで終了 */
		if (mBluetoothAdapter == null)
			ErrDialog.create(MainActivity.this, "Bluetooth未サポートの端末です。\nアプリを終了します。").show();
			/* OFFならONにするようにリクエスト */
		else if( !mBluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			ActivityResultLauncher<Intent> startForResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
					result -> {
						if(result.getResultCode() != Activity.RESULT_OK) {
							ErrDialog.create(MainActivity.this, "BluetoothがOFFです。ONにして操作してください。\n終了します。").show();
						}
						else {
							startBTClientThread();
						}
					});
			startForResult.launch(enableBtIntent);
		}

		startBTClientThread();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		/* 対象外なので、無視 */
		if (requestCode != REQUEST_PERMISSIONS) return;

		/* 権限リクエストの結果を取得する. */
		long ngcnt = Arrays.stream(grantResults).filter(value -> value != PackageManager.PERMISSION_GRANTED).count();
		if (ngcnt > 0) {
			ErrDialog.create(MainActivity.this, "このアプリには必要な権限です。\n再起動後に許可してください。\n終了します。").show();
		}
		else {
			startBTClientThread();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		stopBTClientThread();
	}

	private BTClientThread btClientThread;
	public void startBTClientThread() {
		if(btClientThread!=null) return;	/* 起動済なら、起動不要。 */

		/* Bluetooth未サポート */
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			TLog.d("Bluetooth未サポートの端末.何もしない.");
			return;
		}

		/* 権限が許可されていない */
		if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			TLog.d("Bluetooth権限なし.何もしない.");
			return;
		}

		/* Bluetooth OFF */
		if( !mBluetoothAdapter.isEnabled()) {
			TLog.d("Bluetooth OFF.何もしない.");
			return;
		}

		/* ペアリング済デバイスを取得 */
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
				throw new RuntimeException("すでに権限付与済のはず");
		}

		/* ペアリング済デバイスを取得 */
		Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
		if(devices.size()==0)
			ErrDialog.create(MainActivity.this, "ペアリング済デバイスがありません。\n先にペアリングを終了してください。\n終了します。").show();


		final String[] items = devices.stream().map(i -> i.getName() + " : " + i.getAddress()).toArray(String[]::new);
		new AlertDialog.Builder(MainActivity.this)
				.setTitle("Btサーバ選択")
				.setItems(items, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						BluetoothDevice device = (BluetoothDevice)devices.toArray()[which];
						btClientThread = new BTClientThread(device);
						btClientThread.start();
					}
				})
				.show();
	}

	public void stopBTClientThread() {
		if(btClientThread==null) return;	/* 停止済なら、停止不要。 */
		btClientThread.interrupt();
		btClientThread = null;
	}

	public static final UUID BT_UUID = UUID.fromString("41eb5f39-6c3a-4067-8bb9-bad64e6e0908");
	public class BTClientThread extends Thread {
		private BluetoothDevice	bluetoothDevice = null;
		private BluetoothSocket	bluetoothSocket;
		private InputStream		inputStream;
		private OutputStream	outputStrem;

		public BTClientThread(BluetoothDevice device) {
			if(device==null) throw new RuntimeException("デバイスが選択されてない!!");
			bluetoothDevice = device;
		}

		@Override
		public void run() {
			byte[] incomingBuff = new byte[64];

			try {
				bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(BT_UUID);
			}
			catch(IOException e) {
				throw new RuntimeException("bluetootが無効化してると発生。基本的に起きない。");
			}

			while(true) {
				try {
					TLog.d("接続中...");
					bluetoothSocket.connect();
					break;
				}
				catch(IOException e) {
					TLog.d("ServerアプリがOpenしてない。リトライします");
					try {Thread.sleep(1000);} catch(InterruptedException ignore) {}
//					continue;
				}
			}

			while(true) {
				if(Thread.interrupted()){
					throw new RuntimeException("不要なはず!!");
//						break;
				}

				try {
					String devicename = bluetoothDevice.getName() + " : " + bluetoothDevice.getAddress();
					TLog.d("Connected. {0}", devicename);
					runOnUiThread(() -> {
						((TextView)findViewById(R.id.txtStatus)).setText(String.format("Connected. %s", devicename));
					});

					inputStream = bluetoothSocket.getInputStream();
					outputStrem = bluetoothSocket.getOutputStream();

					while(true) {
						if (Thread.interrupted())
							break;

						String command = new Date().toString() + " aaaaa";
						outputStrem.write(command.getBytes());

						int incomingBytes = inputStream.read(incomingBuff);
						byte[] buff = new byte[incomingBytes];
						System.arraycopy(incomingBuff, 0, buff, 0, incomingBytes);
						String rcvStr = new String(buff, StandardCharsets.UTF_8);

						runOnUiThread(() -> { ((TextView)findViewById(R.id.txtTemp)).setText(rcvStr); });

						// Update again in a few seconds
						try { Thread.sleep(3000); }
						catch(InterruptedException ignore) {}
					}
				}
				catch(IOException e) { e.printStackTrace(); }

				TLog.d("DisConnected.");
				runOnUiThread(() -> { ((TextView)findViewById(R.id.txtStatus)).setText(String.format("DisConnected. - Exit BTClientThread.")); });
			}
		}
	}
}
