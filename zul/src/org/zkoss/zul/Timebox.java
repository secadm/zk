/* Timebox.java


 Purpose:
 
 Description:
 
 History:
 	Jul 9, 2007 10:03:38 AM , Created by Dennis Chen


 Copyright (C) 2007 Potix Corporation. All Rights Reserved.

 {{IS_RIGHT
 This program is distributed under LGPL Version 3.0 in the hope that
 it will be useful, but WITHOUT ANY WARRANTY.
 }}IS_RIGHT
 */
package org.zkoss.zul;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.Locale;

import org.zkoss.lang.Objects;
import org.zkoss.util.Locales;
import org.zkoss.util.TimeZones;
import org.zkoss.text.DateFormats;

import org.zkoss.zk.ui.WrongValueException;
import org.zkoss.zul.impl.FormatInputElement;
import org.zkoss.zul.impl.InputElement;
import org.zkoss.zul.impl.Utils;
import org.zkoss.zul.mesg.MZul;

/**
 * An input box for holding a time (a java.util.Date Object , but only Hour & Minute are used.
 *
 * <p>Default {@link #getZclass}: z-timebox. (since 3.5.0)
 * 
 * <p>timebox supports below key events.
 * <lu>
 *  <li>0-9 : set the time digit depend on the position on the inner text box.
 * 	<li>up : increase time digit depend on the position on the inner text box.
 *  <li>down : decrease time digit depend on the position on the inner text box.
 * 	<li>delete : clear the time to empty (null)
 * </lu>
 *
 * <p>Like {@link Combobox} and {@link Datebox},
 * the value of a read-only time box ({@link #isReadonly}) can be changed
 * by clicking the up or down button (though users cannot type anything
 * in the input box).
 *
 * @author Dennis Chen
 * @since 3.0.0
 */
public class Timebox extends FormatInputElement implements org.zkoss.zul.api.Timebox {
	/*package*/ static final String DEFAULT_FORMAT = "HH:mm";
	private TimeZone _tzone;
	/** The locale assoicated with this timebox. */
	private Locale _locale;
	private boolean _btnVisible = true;
	
	public Timebox() {
		setCols(5);
		setFormat(getDefaultFormat());
	}
	public Timebox(Date date) throws WrongValueException {
		this();
		setValue(date);
	}



	/** Sets the date format.
<p>If null or empty is specified, {@link #getDefaultFormat} is assumed.
Since 5.0.7, you could specify one of the following reserved words,
and {@link DateFormats#getTimeFormat}
will be used to retrieve the real format.
<table border=0 cellspacing=3 cellpadding=0>
<tr>
<td>short</td>
<td>{@link DateFormats#getTimeFormat} with {@link DateFormat#SHORT}</td>
</tr>
<tr>
<td>medium</td>
<td>{@link DateFormats#getTimeFormat} with {@link DateFormat#MEDIUM}</td>
</tr>
<tr>
<td>long</td>
<td>{@link DateFormats#getTimeFormat} with {@link DateFormat#LONG}</td>
</tr>
<tr>
<td>full</td>
<td>{@link DateFormats#getTimeFormat} with {@link DateFormat#FULL}</td>
</tr>
</table>

<p>In additions, the format could be a combination of the following pattern letters:
<table border=0 cellspacing=3 cellpadding=0>

     <tr bgcolor="#ccccff">
         <th align=left>Letter
         <th align=left>Date or Time Component
         <th align=left>Presentation
         <th align=left>Examples
     <tr>
 *     <tr bgcolor="#eeeeff">
 *         <td><code>a</code>
 *         <td>Am/pm marker
 *         <td><a href="#text">Text</a>
 *         <td><code>PM</code>
 *     <tr>
 *         <td><code>H</code>
 *         <td>Hour in day (0-23)
 *         <td><a href="#number">Number</a>
 *         <td><code>0</code>
 *     <tr bgcolor="#eeeeff">
 *         <td><code>k</code>
 *         <td>Hour in day (1-24)
 *         <td><a href="#number">Number</a>
 *         <td><code>24</code>
 *     <tr>
 *         <td><code>K</code>
 *         <td>Hour in am/pm (0-11)
 *         <td><a href="#number">Number</a>
 *         <td><code>0</code>
 *     <tr bgcolor="#eeeeff">
 *         <td><code>h</code>
 *         <td>Hour in am/pm (1-12)
 *         <td><a href="#number">Number</a>
 *         <td><code>12</code>
 *     <tr>
 *         <td><code>m</code>
 *         <td>Minute in hour
 *         <td><a href="#number">Number</a>
 *         <td><code>30</code>
 *     <tr bgcolor="#eeeeff">
 *         <td><code>s</code>
 *         <td>Second in minute
 *         <td><a href="#number">Number</a>
 *         <td><code>55</code>
 </table>
 	@since 5.0.0
 	 */
	public void setFormat(String format) throws WrongValueException {
		if (format == null || format.length() == 0) {
			format = getDefaultFormat();
		} else {
			int ts = Datebox.toStyle(format);
			if (ts != -111)
				format = DateFormats.getTimeFormat(ts, _locale, DEFAULT_FORMAT);
		}
		super.setFormat(format);
	}
	
	/** Returns the value (in Date), might be null unless
	 *  a constraint stops it. And, only Hour and Mintue field is effective.
	 * @exception WrongValueException if user entered a wrong value
	 */
	public Date getValue() throws WrongValueException {
		return (Date)getTargetValue();
	}
	/** Sets the value (in Date).
	 * If value is null, then an empty will be sent(render) to client.
	 * If else, only the Hour and Mintue field will be sent(render) to client. 
	 * 
	 * @exception WrongValueException if value is wrong
	 */
	public void setValue(Date value) throws WrongValueException {
		validate(value);
		setRawValue(value);
	}
	
	/** Returns whether the button (on the right of the textbox) is visible.
	 * <p>Default: true.
	 */
	public boolean isButtonVisible() {
		return _btnVisible;
	}
	/** Sets whether the button (on the right of the textbox) is visible.
	 */
	public void setButtonVisible(boolean visible) {
		if (_btnVisible != visible) {
			_btnVisible = visible;
			smartUpdate("buttonVisible", visible);
		}
	}
		
	/** Returns the time zone that this time box belongs to, or null if
	 * the default time zone is used.
	 * <p>The default time zone is determined by {@link TimeZones#getCurrent}.
	 */
	public TimeZone getTimeZone() {
		return _tzone;
	}
	/** Sets the time zone that this time box belongs to, or null if
	 * the default time zone is used.
	 * <p>The default time zone is determined by {@link TimeZones#getCurrent}.
	 */
	public void setTimeZone(TimeZone tzone) {
		if (_tzone != tzone) {
			_tzone = tzone;
			smartUpdate("_value", marshall(_value));
		}
	}

	/** Returns the locale associated with this timebox,
	 * or null if {@link Locales#getCurrent} is preferred.
	 * @since 5.0.7
	 */
	public Locale getLocale() {
		return _locale;
	}
	/** Sets the locale used to indetify the format of this timebox.
	 * <p>Default: null (i.e., {@link Locales#getCurrent}, the current locale
	 * is assumed)
	 * @since 5.0.7
	 */
	public void setLocale(Locale locale) {
		_locale = locale;
	}
	/** Sets the locale used to indetify the format of this timebox.
	 * <p>Default: null (i.e., {@link Locales#getCurrent}, the current locale
	 * is assumed)
	 * @since 5.0.7
	 */
	public void setLocale(String locale) {
		setLocale(locale != null && locale.length() > 0 ?
			Locales.getLocale(locale): null);
	}

	/**
	 * Returns the default format, which is used when constructing a timebox.
	 * <p>Default: DateFormats.getTimeFormat(DEFAULT, null, "HH:mm")
	 * (see {@link DateFormats#getTimeFormat}).
	 * 
	 * <p>Though you might override this method to provide your own default format,
	 * it is suggested to specify the format for the current thread
	 * with {@link DateFormats#setTimeFormatLocal}.
	 * @since 5.0.7
	 */
	protected String getDefaultFormat() {
		return DateFormats.getTimeFormat(DateFormat.DEFAULT, _locale, "HH:mm");
			//We use HH:mm for backward compatibility
	}

	protected Object marshall(Object value) {
		if (value == null || _tzone == null) return value;
		return new Date(((Date) value).getTime() - TimeZones.getCurrent().getRawOffset() + _tzone.getRawOffset());
	}
	protected Object unmarshall(Object value) {
		if (value == null || _tzone == null) return value;
		return new Date(((Date) value).getTime() + TimeZones.getCurrent().getRawOffset() - _tzone.getRawOffset());
	}
	protected Object coerceFromString(String value) throws WrongValueException {
		//null or empty string,
		if (value == null || value.length() == 0){
			return null;
		}
		final String fmt = getFormat();
		final DateFormat df = getDateFormat(fmt);
		final Date date;
		try {
			date = df.parse(value);
		} catch (ParseException ex) {
			throw showCustomError(
				new WrongValueException(this, MZul.DATE_REQUIRED,
					new Object[] {value, fmt}));
		}
		return date;
	}
	protected String coerceToString(Object value) {
		final DateFormat df = getDateFormat(getFormat());
		return value != null ? df.format((Date) value) : "";
	}
	
	/** Returns the date format of the time only,
	 *
	 * <p>Default: it uses SimpleDateFormat to format the date.
	 */
	protected DateFormat getDateFormat(String fmt) {
		final DateFormat df = new SimpleDateFormat(fmt,
			_locale != null ? _locale: Locales.getCurrent());
		final TimeZone tz = _tzone != null ? _tzone : TimeZones.getCurrent();
		df.setTimeZone(tz);
		return df;
	}

	// super
	public String getZclass() {
		return _zclass == null ?  "z-timebox" : _zclass;
	}
	protected void renderProperties(org.zkoss.zk.ui.sys.ContentRenderer renderer)
	throws java.io.IOException {
		super.renderProperties(renderer);

		if(_btnVisible != true)
			renderer.render("buttonVisible", _btnVisible);
	}
}
