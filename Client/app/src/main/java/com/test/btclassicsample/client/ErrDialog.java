package com.test.btclassicsample.client;

import android.app.AlertDialog;
import android.content.Context;

public class ErrDialog {
	/* AlertDialog生成 */
	public static AlertDialog.Builder create(Context context, String ErrStr) {
		return new AlertDialog.Builder(context)
				.setMessage(ErrStr)
				.setNegativeButton("終了", (dialog, id) -> {
					android.os.Process.killProcess(android.os.Process.myPid());
				});
	}
}
