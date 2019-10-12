# Unofficial Tile API for Wear OS

In early May 2019, Wear OS added *Tiles*, a widget-like UI for viewing snippets of app data alongside your watch face. Unfortunately, they didn't release an API at the same time - meaning Tiles were limited to just Google (and select partners). As an Android dev with a long history of widget work, I wanted in on the fun.

So, I extracted the relevant code bits from the Wear OS app, and extrapolated the API. Hey presto, [it works](<https://youtu.be/Wm8eitGBKhw)!

NOTE: This is **very much** a work in progress, and there are still plenty of rough edges. I'll be updating this repo as work progresses.

NOTE 2: Although the API detailed below is undeniably unofficial, it does use the official Wear system-level hooks to create and update tiles on the watch. They're real Tiles in the end.

NOTE 3: FYI, code samples below are in Kotlin. If you're still using Java, you'll need to translate accordingly.

NOTE 4: In June 2019, Google added a *My Tiles* screen to the Wear OS phone app. While Tiles built with this unofficial API will appear on that menu, I haven't yet found a way to configure a preview image. If you have any ideas on how to accomplish this, please share!

## Installation
*UnofficialTileAPI* is a standard Android library, so just grab the source from this site and include it in your project like you would any other. If you haven't used one before, you can find general instructions for adding a library to your project here: https://stackoverflow.com/q/20377591/252080

Specifically for this, you'll need to download the source from here (there's a Download link in the nav pane on the left), unzip it to your project dir, and make sure the name you use when unzipping matches what you put in your *.gradle files.

## Use
It turns out that Tiles are widget-like in more than just appearance; they're based on [`RemoteViews`](https://developer.android.com/reference/android/widget/RemoteViews.html), same as an App Widget on an Android phone. So in order to provide a Tile, you'll need to build off that framework.

Once you have that in place, you'll need to extend `TileProviderService` and override `onTileUpdate`. When your `RemoteViews` are ready for display, you'll need to call the superclass' `sendData` method. More on both of these below.

### Events

Since you're extending the `TileProviderService` class, most functionality will go in events that you override, listed below. The Tile-specific events all get passed an integer parameter, apparently a consistent *tile ID*. These seem to get assigned sequentially when Tiles are added, remain unchanged throughout the tile's lifetime, and don't get reused. [Again, if you've ever done an App Widget, this is exactly like the `appWidgetId` they use.]

Note that you'll need this tile ID as the first parameter when calling `TileProviderService.sendData`, so it's a good idea to keep it around in a field.

### `onTileUpdate(tileId: Int)`

This method seems to be called **once** when your Tile is first added (or at boot), with your ID as its parameter. Thereafter, it'll be called occasionally with large negative numbers as a parameter; these obviously aren't your tile ID, but they aren't consistent either, so I'm not sure yet what they're about.

My experience is that your `onTileUpdate` implementation should check for these "dummy" values; the superclass provides a boolean `isIdForDummyData()` method. If the ID passes, save it for future reference, and probably do an initial tile update.

### `onTileFocus(tileId: Int)` and `onTileBlur(tileId: Int)`
These work pretty much as you'd expect, though perhaps not exactly: `onTileFocus` sometimes gets called when the user swipes onto your tile, but sometimes only when they swipe onto the tile area more generally (*not* your specific tile). So it's somewhat inconsistent - but it always seems to happen before your tile comes on screen. And naturally, `onTileBlur` is the inverse, firing when the user leaves the tile(s).

In any case, these events are the natural places for  work like starting and stopping ongoing tile updates.

### `onCreate()` and `onDestroy()`
Since `TileProviderService` is a subclass of `Service`, you'll get the usual lifecycle methods. So they make sense for any per-process initialization and cleanup, respectively.

Like any Android process, however, your tile provider may be shut down and removed from memory anytime it's not on screen - so code accordingly.

## Tile Updates

When you've built your Tile's `RemoteViews` (which, yes, can be a PITA), you need to hand them off to the system for display. There are two more pieces of the API you'll use for this.

### `TileData.Builder`

This is a basic builder class that takes your `RemoteViews` and bundles them up for sending. It's quite simple in its use:

    TileData.Builder().setRemoteViews(myRemoteViews).build())

The returned `TileData` object is a thin wrapper around a standard `Bundle`, and is ready for use with... 
 
### `sendData(tileId: Int, data: TileData)`

Another superclass method, `sendData` is where the rubber meets the road: pass it the `TileData` you built in the previous section, and if all goes well, the system will display your `RemoteViews` in your Tile.

I say *if all goes well* because `RemoteViews` aren't very forgiving. If you got something wrong while putting yours together, it's likely to just fail silently, and give you a blank tile for your trouble. If you're lucky, you may see a `RemoteViews` error in logcat - but I generally haven't, and even if one does appear, it's easy to miss because it won't come from your process.

### Scheduling regular updates
Some Tiles get by with static data, but most will want updating periodically, so it'd be nice if there was a built-in way to schedule this. Looking at Google's own tiles, I found two approaches:

- A manifest `meta-data` element of `com.google.android.clockwork.tiles.UPDATE_PERIOD_SECONDS` is used in the official Agenda tile (with a value of *300*)
- A call to `TileData.Builder.setOutdatedTimeMs()` is  used in the official News tile (with a value of *now + 1 hour*)

However, in my testing, **neither of these techniques work**. I tried both, numerous times, with various values, and never saw a callback to my `onTileUpdate` method (or anything else, for that matter).

So if your Tile needs recurring updates, it looks like you'll need to roll your own, with one of the standard Android techniques for such things - such as a `Pendingintent` or `JobScheduler` for long-period updates, or a `Handler` or `Coroutine` for short-period ones. I'm using the latter in the sample code below, FWIW, but you do what works best for you.


## Code
Enough documentation! Here's a simple Tile you can use as a template, that updates its data once per second while visible:

```kotlin
package my.packagename

import android.util.Log
import android.widget.RemoteViews

import com.google.android.clockwork.tiles.`TileData$Builder`
import com.google.android.clockwork.tiles.TileProviderService
import kotlinx.coroutines.*


class MyTileProviderService : TileProviderService() {

    private var id: Int = -1
    private var updateJob: Job? = null

    override fun onTileUpdate(tileId: Int) {
        Log.d(TAG, "onTileUpdate() called with: tileId = [$tileId]")
        
        if (!isIdForDummyData(tileId)) {
            tileId = id
            sendRemoteViews()
        }
    }

    override fun onTileFocus(tileId: Int) {
        Log.d(TAG, "onTileFocus() called with: tileId = [$tileId]")
        
        id = tileId
        
        updateJob?.cancel()
        updateJob = CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                sendRemoteViews()
                delay(1000)
            }
        }
    }

    override fun onTileBlur(tileId: Int) {
        Log.d(TAG, "onTileBlur() called with: tileId = [$tileId]")
        
        updateJob?.cancel()
    }

    private fun sendRemoteViews() {
        Log.d(TAG, "sendRemoteViews")
        
        val remoteViews = RemoteViews(this.packageName, R.layout.tile)
        // *** Update your tile UI here

        val bob = TileData.Builder()
                .setRemoteViews(remoteViews)
        sendData(id, bob.build())
    }

    companion object {
        private const val TAG = "MyTileProviderService"
    }
}
```

And of course, you'll also need it in your manifest:

```xml
<service
    android:label="@string/tile_name"
    android:icon="@drawable/ic_launcher"
    android:name=".MyTileProviderService"
    android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER"
    android:exported="true"
    >
    <intent-filter>
        <action android:name="com.google.android.clockwork.ACTION_TILE_UPDATE_REQUEST" />
    </intent-filter>
</service>
```
A couple of quick notes about that manifest entry:

- Your Tile will appear in the chooser with the `android:label` attribute as its title and your app name as its subtitle. You can supply a different subtitle by supplying an `android:description` attribute, if you wish.
- Both the `android:permission` attribute and the `intent-filter` element above are required for showing a Tile.

## Disclaimers

- There's no guarantee that this code won't break at any moment. I'm still figuring this stuff out. 
- Given it's not a published API, Google may break it from their side.
- There's **certainly** no guarantee that it will be compatible with any Tile API that Google may eventually release.
- And just in case it wasn't clear, I have no affiliation with Google Inc

## Stuff to ponder
 
What are those funny negative inputs to `onTileUpdate`?

---
Given Tiles share so much foundation with AppWidgets, could we hope to see [collection-based](https://developer.android.com/guide/topics/appwidgets#collections) Tiles someday?

---
While rummaging around in the source, I found reference to `MultipleTiles`, in a package named `googledata.experiments.mobile.wear.features`. Now **that's** tantalizing! I could certainly see good uses for an arbitrary number of tiles from a single app; perhaps this is a hint that such support is in the works?

---

## Apps with Tiles

Since I first released this unofficial API, several developers have integrated Tiles into their Wear OS apps (including me). Here are the apps I'm aware of:

- [Bubble Cloud](https://play.google.com/store/apps/details?id=dyna.logix.bookmarkbubbles)

- [JellyLauncher](https://play.google.com/store/apps/details?id=vg.maarten.jellylauncher)

- [Lunescope](https://play.google.com/store/apps/details?id=com.daylightmap.moon.pro.android)

- [Nav Explorer](https://play.google.com/store/apps/details?id=com.turndapage.navexplorer)

- [Nav Fit](https://play.google.com/store/apps/details?id=com.turndapage.navfit)

- [NavMusic](https://play.google.com/store/apps/details?id=com.turndapage.navmusic)

- [TerraTime](https://play.google.com/store/apps/details?id=com.daylightclock.android): 2 tiles, *Daylight Map* and *Daylight Wave*

- [Wearable Widgets](https://play.google.com/store/apps/details?id=com.wearablewidgets) *Coming Soon!*

If you know of others, drop me a line at *sterling@udell.dev* and I'll add them to the list.
