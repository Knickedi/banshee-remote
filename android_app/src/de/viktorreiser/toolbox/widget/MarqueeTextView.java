package de.viktorreiser.toolbox.widget;

import android.content.Context;
import android.graphics.Rect;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * Text view where marquee is always working.<br>
 * <br>
 * This is a hack which allows to create a text view where marquee is always working. Don't use it
 * for other things! This text view is always focused and selected!<br>
 * <br>
 * Idea for this hack is from here:<br>
 * http://stackoverflow.com/questions/1827751/is-there-a-way-to-make-ellipsize-marquee-always-scroll/2504840#2504840
 * 
 * @author Viktor Reiser &lt;<a href="mailto:viktorreiser@gmx.de">viktorreiser@gmx.de</a>&gt;
 */
public class MarqueeTextView extends TextView {

	public MarqueeTextView(Context context) {
		super(context);
		marqueeInit();
	}
	
	public MarqueeTextView(Context context, AttributeSet attrs) {
		super(context, attrs);
		marqueeInit();
	}
	
	public MarqueeTextView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		marqueeInit();
	}
	
	private void marqueeInit() {
		setSelected(true);
		setEllipsize(TruncateAt.MARQUEE);
		setMarqueeRepeatLimit(-1);
		setSingleLine();
		setHorizontallyScrolling(true);
	}
	
	
	@Override
	protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
	    if (focused) {
	    	super.onFocusChanged(focused, direction, previouslyFocusedRect);
	    }
	}
	
	@Override
	public void onWindowFocusChanged(boolean focused) {
	    if (focused) {
	    	super.onWindowFocusChanged(focused);
	    }
	}

	@Override
	public boolean isFocused() {
	    return true;
	}
}
