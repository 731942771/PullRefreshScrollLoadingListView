package com.cuiweiyou.pullandscrolllistview;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ArrayAdapter;

import com.cuiweiyou.pullandscrolllistview.PureListView.IUpdateOrLoadBack;
/** 布局中使用自定义ListView实现下拉刷新上滑加载 **/
public class MainActivity extends Activity {

	private List<String> list;
	private PureListView mListView;	// 自定义LV
	private ArrayAdapter<String> arrayAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		initData();
		initView();
	}

	private void initData() {
		list = new ArrayList<String>();
		for (int i = 0; i < 16; i++) {
			list.add("条目：" + i);
		}
	}

	private void initView() {
		mListView = (PureListView) findViewById(R.id.mylv);
		mListView.setUpdateOrLoadBack(new IUpdateOrLoadBack() {
			@Override
			public void updateOrLoadBack(int flag) {
				switch (flag) {
					case 0:	// 下拉刷新
						new Handler().postDelayed(new Runnable() {
							@Override
							public void run() {
								// 重置ListView。一般是在AsyncTask中访问网络获取数据
								mListView.reset(0);
								// TODO 刷新适配器
							}
						}, 3000);
					break;
					case 1:	// 上滑加载
						new Handler().postDelayed(new Runnable() {
							@Override
							public void run() {
								mListView.reset(1);
								// TODO 刷新适配器
							}
						}, 3000);
					break;
				}
			}
		});
		arrayAdapter = new ArrayAdapter<String>(this, R.layout.item_listview, R.id.tv_item, list);
		mListView.setAdapter(arrayAdapter);
	}
}
