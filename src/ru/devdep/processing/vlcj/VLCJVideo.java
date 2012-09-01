/**
 * ##library.name##
 * ##library.sentence##
 * ##library.url##
 *
 * Copyright ##copyright## ##author##
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General
 * Public License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA  02111-1307  USA
 * 
 * @author      ##author##
 * @modified    ##date##
 * @version     ##library.prettyVersion## (##library.version##)
 */

package ru.devdep.processing.vlcj;

import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PImage;

import uk.co.caprica.vlcj.binding.LibVlc;
import uk.co.caprica.vlcj.player.MediaPlayer;
import uk.co.caprica.vlcj.player.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.direct.DirectMediaPlayer;
import uk.co.caprica.vlcj.player.direct.RenderCallback;
import uk.co.caprica.vlcj.player.events.MediaPlayerEventType;
import uk.co.caprica.vlcj.runtime.RuntimeUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import com.sun.jna.Memory;

import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;

public class VLCJVideo extends PImage implements PConstants, RenderCallback {

	public final static String VERSION = "##library.prettyVersion##";

	protected int[] copyPixels = null;
	protected PApplet parent = null;

	public int width;
	public int height;

	protected String filename;
	protected Boolean firstFrame;

	protected MediaPlayerFactory factory;
	protected DirectMediaPlayer mediaPlayer;

	protected static Boolean inited = false;
	public static String vlcLibPath = "";

	protected final HashMap<MediaPlayerEventType, ArrayList<Runnable>> handlers;

	public static void setVLCLibPath(String path) {
		vlcLibPath = path;
	}

	public void bind(MediaPlayerEventType type, Runnable handler) {
		ArrayList<Runnable> eventHandlers;
		if (!handlers.containsKey(type)) {
			eventHandlers = new ArrayList<Runnable>();
			handlers.put(type, eventHandlers);
		} else {
			eventHandlers = handlers.get(type);
		}
		eventHandlers.add(handler);
	}

	public void handleEvent(MediaPlayerEventType type) {
		if (handlers.containsKey(type)) {
			ArrayList<Runnable> eventHandlers = handlers.get(type);
			Iterator<Runnable> it = eventHandlers.iterator();
			while (it.hasNext()) {
				it.next().run();
			}
		}
	}

	protected static void init() {
		if (inited)
			return;

		inited = true;

		if (vlcLibPath == "") {
			if (PApplet.platform == MACOSX) {
				vlcLibPath = "/Applications/VLC.app/Contents/MacOS/lib";
			} else if (PApplet.platform == WINDOWS) {
				vlcLibPath = "C:\\Program Files\\VideoLAN\\VLC";
			} else if (PApplet.platform == LINUX) {
				vlcLibPath = "/home/linux/vlc/install/lib";
			}
		}
		NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcLibraryName(),
				vlcLibPath);
		Native.loadLibrary(RuntimeUtil.getLibVlcLibraryName(), LibVlc.class);
	}

	public VLCJVideo(PApplet parent, String... options) {
		super(0, 0, PApplet.RGB);
		width = parent.width;
		height = parent.height;
		VLCJVideo.init();
		handlers = new HashMap<MediaPlayerEventType, ArrayList<Runnable>>();
		initVLC(parent, options);
	}

	protected void initVLC(PApplet parent, String... options) {
		this.parent = parent;
		firstFrame = true;
		parent.registerDispose(this);
		factory = new MediaPlayerFactory(options);
		mediaPlayer = factory.newDirectMediaPlayer(width, height, this);
		bindMediaPlayerEvents();
	}

	protected void bindMediaPlayerEvents() {
		mediaPlayer.addMediaPlayerEventListener(new MediaPlayerEventAdapter() {

			public void opening(MediaPlayer mp) {
				handleEvent(MediaPlayerEventType.OPENING);
			}

			public void error(MediaPlayer mediaPlayer) {
				handleEvent(MediaPlayerEventType.ERROR);
			}

			public void finished(MediaPlayer mediaPlayer) {
				handleEvent(MediaPlayerEventType.FINISHED);
			}

			public void paused(MediaPlayer mediaPlayer) {
				handleEvent(MediaPlayerEventType.PAUSED);
			}

			public void stopped(MediaPlayer mediaPlayer) {
				handleEvent(MediaPlayerEventType.STOPPED);
			}

			public void playing(MediaPlayer mediaPlayer) {
				handleEvent(MediaPlayerEventType.PLAYING);
			}

			public void mediaStateChanged(MediaPlayer mediaPlayer, int newState) {
				handleEvent(MediaPlayerEventType.MEDIA_STATE_CHANGED);
			}

		});
	}

	public void openMedia(String mrl) {
		copyPixels = new int[width * height];
		try {
			filename = parent.dataPath(mrl);
			File f = new File(filename);
			if (!f.exists()) {
				filename = mrl;
			}
		} finally {
			mediaPlayer.prepareMedia(filename);
		}
	}

	public void play() {
		mediaPlayer.play();
	}

	public void stop() {
		mediaPlayer.stop();
	}

	public void pause() {
		mediaPlayer.pause();
	}

	public float time() {
		return (float) ((float) mediaPlayer.getTime() / 1000.0);
	}

	public float duration() {
		return (float) ((float) mediaPlayer.getLength() / 1000.0);
	}

	public void jump(float pos) {
		mediaPlayer.setTime(Math.round(pos * 1000));
	}

	public void loop() {
		mediaPlayer.setRepeat(true);
	}

	public void noLoop() {
		mediaPlayer.setRepeat(false);
	}

	public void volume(float volume) {
		if (volume < 0.0) {
			volume = (float) 0.0;
		} else if (volume > 1.0) {
			volume = (float) 1.0;
		}
		mediaPlayer.setVolume(parent.round((float) (200.0) * volume));
	}

	public synchronized void display(Memory memory) {
		memory.read(0, copyPixels, 0, width * height);
		if (firstFrame) {
			super.init(width, height, parent.ARGB);
			firstFrame = false;
		}
		pixels = copyPixels;
		updatePixels();
	}

	public void dispose() {
		if (mediaPlayer != null) {
			if (mediaPlayer.isPlaying()) {
				mediaPlayer.stop();
			}
			mediaPlayer.release();
		}
		factory.release();
		copyPixels = null;
	}

	protected void finalize() throws Throwable {
		try {
			dispose();
		} finally {
			super.finalize();
		}
	}

}
