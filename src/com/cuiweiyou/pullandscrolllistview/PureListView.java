package com.cuiweiyou.pullandscrolllistview;

import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 自定义ListView，添加滚动刷新功能
 * @author cuiweiyou.com
 */
public class PureListView extends ListView {
	
	private Context context;

	/** LV的头实例 **/
	private View mHeaderView;
	/** LV的尾实例 **/
	private View mFooterView;
	
	/** LV头实际高度 **/
	private int measuredHeaderHeight;
	/** LV尾实际高度 **/
	private int measuredFooterHeight;
	
	/** LV头中的状态文本 **/
	private TextView mHeaderState;
	/** LV尾中的状态文本 **/
	private TextView mFooterState;
	/** LV头中的状态动画 **/
	private ImageView mHeaderAnim;
	/** LV尾中的状态动画 **/
	private ImageView mFooterAnim;
	/** 头状态动画实体 **/
	private AnimationDrawable animHeader;
	/** 尾状态动画实体 **/
	private AnimationDrawable animFooter;
	
	/** LV当前最顶端显示的item的索引 **/
	private int indexTopItem;
	/** LV当前最底端显示的item的索引 **/
	private int indexBottomItem;
	/** Item总数量，header、footer也算 **/
	private int itemCount;
	/** 手指按下的位置y **/
	private float downRawY;
	
	/** 通知主UI的刷新回调 **/
	private IUpdateOrLoadBack updateOrLoadBack;
	/** 是否正在请求数据（刷新或加载） **/
	private boolean isRequesting;
	
	public PureListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = context;
		
		initView(context);
	}

	private void initView(Context context) {
		// 1.定义一个LV的头
		mHeaderView = View.inflate(getContext(), R.layout.view_header_lv_hmaty, null);
		mFooterView = View.inflate(getContext(), R.layout.view_footer_lv_hmaty, null);
		// 2.宽高模式
		int widthMeasure = MeasureSpec.makeMeasureSpec(LinearLayout.LayoutParams.MATCH_PARENT, MeasureSpec.EXACTLY); // 占满宽度
		int heightMeasure = MeasureSpec.makeMeasureSpec(LinearLayout.LayoutParams.WRAP_CONTENT, MeasureSpec.UNSPECIFIED); // 高度无限制
		// 3.指定模式
		mHeaderView.measure(widthMeasure, heightMeasure);
		mFooterView.measure(widthMeasure, heightMeasure);
		// 4.得到实际宽高
		measuredHeaderHeight = mHeaderView.getMeasuredHeight();
		measuredFooterHeight = mFooterView.getMeasuredHeight();
		// 5.相对于LV的内边距：（左，上，右，下），把头移出了可视区域
		mHeaderView.setPadding(0, -measuredHeaderHeight, 0, 0);
		mFooterView.setPadding(0, 0, 0, -measuredFooterHeight);
		// 6.为标识控件设置动画
		initAnimHeader();
		// 标识控件
		mHeaderAnim = (ImageView) mHeaderView.findViewById(R.id.iv_header_anim);
		mHeaderAnim.setImageDrawable(animHeader);//将序列帧动画作为控件的图片
		initAnimFooter();
		mFooterAnim = (ImageView) mFooterView.findViewById(R.id.iv_footer_anim);
		mFooterAnim.setImageDrawable(animFooter);
		mHeaderState = (TextView) mHeaderView.findViewById(R.id.tv_header_state);
		mFooterState = (TextView) mFooterView.findViewById(R.id.tv_footer_state);
		// 7.添加header头
		this.addHeaderView(mHeaderView, null, false);
		this.addFooterView(mFooterView, null, false);
	}

	/**
	 * onTouchEvent早于onScroll事件处理<br/>
	 * 首先touch了，才会随着手指scroll
	 */
	@Override
	public boolean onTouchEvent(MotionEvent ev) {

		switch (ev.getAction()) {
	
			case MotionEvent.ACTION_DOWN:
				downRawY = ev.getRawY();

				// 最顶端条目的索引。header和footer都是一个条目（item），
				// header和footer的visible-可见属性默认都是VISIBLE-可见，但VISIBLE、INVISIBLE、GONE都无所谓。getXVisible大概是“取可用”
				// 因padding值设为负数，所以肉眼可见最顶为第一条item时，进行取值得到的不是看到的这个item的索引，实际是header的索引0
				indexTopItem = getFirstVisiblePosition();	// 如果显示最顶item，得到header索引=0
				indexBottomItem = getLastVisiblePosition();	// 如果显示最底item，得到footer索引=总条目数量-1
				
				itemCount = getAdapter().getCount();
				
				break;
	
			case MotionEvent.ACTION_MOVE:
				
				// 如果不是最顶，也不是最底，系统处理。否则要么最顶下拉；要么最底上滑
				if(indexTopItem != 0 && indexBottomItem != itemCount - 1)
					return super.onTouchEvent(ev);
				
				// 如果当前正处于上次操作导致的刷新或加载过程中，就不再进行下拉或上滑响应
				if(isRequesting)
					return super.onTouchEvent(ev);
				
				float nowRawY = ev.getRawY();
				float distanceY = nowRawY - downRawY;
	
				//////////// 下拉刷新 ////////////////////////////
				if(indexTopItem == 0){
					
					if (distanceY > 0) {
						
						mHeaderView.setPadding(0, (int) (distanceY - measuredHeaderHeight), 0, 0);
		
						if (distanceY > measuredHeaderHeight * 1.5) {	// 达到一定距离，对header中的子控件进行操作
							mHeaderState.setText("松开手指刷新");
							animHeader.start();
						} else {
							mHeaderState.setText("继续下拉刷新");
							animHeader.stop();
						}
						
						if (distanceY > measuredHeaderHeight * 2.5) {	// 达到一定距离，固定header的位置
							mHeaderView.setPadding(0, (int) (measuredHeaderHeight * 1.5), 0, 0);
						}
		
						return true;	// 手指先向下滑继而又向上滑，上滑时会默认增加LV默认上滚的值。true屏蔽这个滚动值
					}
				}
				
				//////////// 上滑加载 ////////////////////////////
				if(indexBottomItem == itemCount - 1){
					mFooterView.setPadding(0, 0, 0, (int) (-measuredFooterHeight + Math.abs(distanceY)));
	
					if (Math.abs(distanceY) > measuredFooterHeight * 1.5) {
						mFooterState.setText("松开手指加载");
						animFooter.start();
					} else {
						mFooterState.setText("继续上滑加载");
						animFooter.stop();
					}

					if (Math.abs(distanceY) > measuredFooterHeight * 2.5) {	// 达到一定距离，对footer中的子控件进行操作
						mFooterView.setPadding(0, 0, 0, (int) (measuredFooterHeight * 1.5));
					}
					
					//return true; // !不能这样，否则上滑不了
				}
				break;
	
			case MotionEvent.ACTION_UP: // 手指抬起
				float upRawY = ev.getRawY();
				float distanceUPY = upRawY - downRawY;
	
				if(indexTopItem != 0 && indexBottomItem != itemCount - 1)
					return super.onTouchEvent(ev);
				if(isRequesting)
					return super.onTouchEvent(ev);
				
				//////////// 下拉处理 /////////////////////////////
				if(indexTopItem == 0){
					if (distanceUPY > measuredHeaderHeight * 2.5) {	// 达到一定距离，对footer中的子控件进行操作
						isRequesting = true;
						mHeaderState.setText("正在执行刷新");
						animHeader.start();
						updateOrLoadBack.updateOrLoadBack(0);
					}
					
					if(distanceUPY <= measuredHeaderHeight * 1.5){
						animHeader.stop();
						mHeaderView.setPadding(0, -measuredHeaderHeight, 0, 0);
						mHeaderState.setText("继续下拉刷新");
						setTop(0);	// 恢复最顶端原可见状态
					}
				}
				
				//////////// 上滑处理 /////////////////////////////
				if(indexBottomItem == itemCount - 1){
					if ( Math.abs(distanceUPY) > measuredFooterHeight * 2.5) {
						isRequesting = true;
						mFooterState.setText("正在执行加载");
						animFooter.start();
						updateOrLoadBack.updateOrLoadBack(1);
					}
					
					if(Math.abs(distanceUPY) <= measuredFooterHeight * 1.5){
						animFooter.stop();
						mFooterView.setPadding(0, 0, 0, -measuredFooterHeight);
						mFooterState.setText("继续上滑刷新");
					}
				}
				
				break;

		}

		return super.onTouchEvent(ev);
	}
	
	/**
	 * 重置，header、footer复位
	 * @param flag 0-header复位，1-footer复位
	 */
	public void reset(int flag){
		switch(flag){
			case 0:
				// TODO 动画复位
				animHeader.stop();
				mHeaderView.setPadding(0, -measuredHeaderHeight, 0, 0);
				mHeaderState.setText("继续下拉刷新");
				setTop(0);	// 恢复最顶端原可见状态
				isRequesting = false;
				
				Toast toast = Toast.makeText(context, "数据已经刷新", Toast.LENGTH_LONG);
				toast.setGravity(Gravity.TOP, 0, 200);	// 在屏幕顶端显示，距离屏幕顶边200px
				toast.show();
			break;
			case 1:
				animFooter.stop();
				// 复位动画免了，没多大意义
				mFooterView.setPadding(0, 0, 0, -measuredFooterHeight);
				mFooterState.setText("继续上滑刷新");
				isRequesting = false;

				Toast toast2 = Toast.makeText(context, "数据已经加载", Toast.LENGTH_LONG);
				toast2.setGravity(Gravity.BOTTOM, 0, 200);	// 在屏幕底部显示，距离屏幕底边200px
				toast2.show();
			break;
		}
	}
	
	/**
	 * 设置加载或更新数据完成后的回调
	 * @param loadback
	 */
	public void setUpdateOrLoadBack(IUpdateOrLoadBack updateOrLoadBack){
		this.updateOrLoadBack = updateOrLoadBack;
	}
	
	/**
	 * 回调接口。用户主UI中操作ListView进行更新<br/>
	 * 调用方法：updateOrLoadBack(int flag)<br/>
	 * flag：0-下拉刷新，1-上滑加载
	 * @author Administrator
	 */
	public interface IUpdateOrLoadBack{
		/**
		 * 回调方法。用于下拉或上滑后通知主UI中异步加载数据
		 * @param flag 0-下拉刷新，1-上滑加载
		 */
		public void updateOrLoadBack(int flag);
	}
	
	/**
	 * SCROLL_STATE_IDLE = 0，ListView滚动结束
	 * SCROLL_STATE_TOUCH_SCROLL = 1，手还在ListView上
	 * SCROLL_STATE_FLING = 2，手离开后ListView还在“飞-继续滚动”中
	 */
//	@Override
	public void onScrollStateChangedXXX(AbsListView view, int scrollState) {
		
//		SCROLL_STATE = scrollState;
	}

	private void initAnimHeader() {
		Drawable d1 = getContext().getResources().getDrawable(R.drawable.longmao_p01);
		Drawable d2 = getContext().getResources().getDrawable(R.drawable.longmao_p02);
		Drawable d3 = getContext().getResources().getDrawable(R.drawable.longmao_p03);
		Drawable d4 = getContext().getResources().getDrawable(R.drawable.longmao_p04);
		Drawable d5 = getContext().getResources().getDrawable(R.drawable.longmao_p05);
		Drawable d6 = getContext().getResources().getDrawable(R.drawable.longmao_p06);
		Drawable d7 = getContext().getResources().getDrawable(R.drawable.longmao_p07);
		Drawable d8 = getContext().getResources().getDrawable(R.drawable.longmao_p08);
		animHeader = new AnimationDrawable();
		animHeader.addFrame(d1, 100);
		animHeader.addFrame(d2, 100);
		animHeader.addFrame(d3, 100);
		animHeader.addFrame(d4, 100);
		animHeader.addFrame(d5, 100);
		animHeader.addFrame(d6, 100);
		animHeader.addFrame(d7, 100);
		animHeader.addFrame(d8, 100);
		animHeader.setOneShot(false);//开启循环播放
	}
	
	private void initAnimFooter() {
		Drawable a1 = getContext().getResources().getDrawable(R.drawable.weishi_01);
		Drawable a2 = getContext().getResources().getDrawable(R.drawable.weishi_02);
		Drawable a3 = getContext().getResources().getDrawable(R.drawable.weishi_03);
		Drawable a4 = getContext().getResources().getDrawable(R.drawable.weishi_04);
		Drawable a5 = getContext().getResources().getDrawable(R.drawable.weishi_05);
		Drawable a6 = getContext().getResources().getDrawable(R.drawable.weishi_06);
		Drawable a7 = getContext().getResources().getDrawable(R.drawable.weishi_07);
		Drawable a8 = getContext().getResources().getDrawable(R.drawable.weishi_08);
		Drawable a9 = getContext().getResources().getDrawable(R.drawable.weishi_09);
		animFooter = new AnimationDrawable();
		animFooter.addFrame(a1, 100);
		animFooter.addFrame(a2, 100);
		animFooter.addFrame(a3, 100);
		animFooter.addFrame(a4, 100);
		animFooter.addFrame(a5, 100);
		animFooter.addFrame(a6, 100);
		animFooter.addFrame(a7, 100);
		animFooter.addFrame(a8, 100);
		animFooter.addFrame(a9, 100);
		animFooter.setOneShot(false);//开启循环播放
	}
}
