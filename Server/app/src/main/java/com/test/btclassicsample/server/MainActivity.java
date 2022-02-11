package com.test.btclassicsample.server;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
	private final static int		REQUEST_PERMISSIONS = 1111;
	private BluetoothAdapter		mBluetoothAdapter;
	BTServerThread					btServerThread;
	private ArrayAdapter<String>	mConversationArrayAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		TLog.d("");

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
//						else {
//							bindUwsService();
//							bindUwsService();
//							bindUwsService();
//						}
					});
			startForResult.launch(enableBtIntent);
		}

		mConversationArrayAdapter = new ArrayAdapter<>(MainActivity.this, R.layout.message);
		((ListView)findViewById(R.id.lvwConversation)).setAdapter(mConversationArrayAdapter);
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
	}

	@Override
	protected void onResume() {
		super.onResume();
		TLog.d("");

		if(mBluetoothAdapter == null) {
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			if (mBluetoothAdapter == null) {
				TLog.d("This device doesn't support Bluetooth.");
			}
		}

		btServerThread = new BTServerThread();
		btServerThread.start();
	}

	@Override
	protected void onPause() {
		super.onPause();
		TLog.d("");
		if(btServerThread != null){
			btServerThread.cancel();
			btServerThread = null;
		}
	}

	public static final String BT_NAME = "BTTEST1";
	public static final UUID BT_UUID = UUID.fromString("41eb5f39-6c3a-4067-8bb9-bad64e6e0908");
	public class BTServerThread extends Thread {
		BluetoothServerSocket	bluetoothServerSocket;
		BluetoothSocket			bluetoothSocket;
		InputStream				inputStream;
		OutputStream			outputStream;

		@Override
		public void run() {
			byte[] incomingBuff = new byte[64];
			while(true) {
				if(Thread.interrupted()) {
					throw new RuntimeException("不要なはず!!");
//					break;
				}

				try{
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
						if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
							throw new RuntimeException("すでに権限付与済のはず");
					}

					bluetoothServerSocket = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(BT_NAME, BT_UUID);
					TLog.d("Client接続待ち...");
					bluetoothSocket = bluetoothServerSocket.accept();
					bluetoothServerSocket.close();
					bluetoothServerSocket = null;

					inputStream = bluetoothSocket.getInputStream();
					outputStream = bluetoothSocket.getOutputStream();

					TLog.d("初期化完了");

					while(true) {
						if (Thread.interrupted()) {
							break;
						}

						TLog.d("受信待ち...");
						int incomingBytes = inputStream.read(incomingBuff);
						byte[] buff = new byte[incomingBytes];
						System.arraycopy(incomingBuff, 0, buff, 0, incomingBytes);
						String cmd = new String(buff, StandardCharsets.UTF_8);

						TLog.d("cmd : {0}", cmd);
						runOnUiThread(() -> { mConversationArrayAdapter.add(cmd); });
						outputStream.write((cmd + " OK").getBytes(StandardCharsets.UTF_8));
					}
				}
				catch(IOException e) { e.printStackTrace();}

				if (bluetoothSocket != null) {
					try {
						bluetoothSocket.close();
						bluetoothSocket = null;
					}
					catch(IOException ignore) {}
				}

				TLog.d("再接続 3秒待機...");
				try{ Thread.sleep(3 * 1000); }
				catch(InterruptedException ignore) {}
			}
		}

		public void cancel() {
			TLog.d("");
			if(bluetoothServerSocket != null) {
				try { bluetoothServerSocket.close(); } catch(IOException ignore) { }
				bluetoothServerSocket = null;
				super.interrupt();
			}
		}
	}
}