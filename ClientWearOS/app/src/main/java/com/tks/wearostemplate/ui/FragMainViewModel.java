package com.tks.wearostemplate.ui;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class FragMainViewModel extends ViewModel {
	private final MutableLiveData<Boolean>	mUnLock			= new MutableLiveData<>(true);
	public MutableLiveData<Boolean>			UnLock()		{ return mUnLock; }
}
