package de.tap.easy_xkcd

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import androidx.annotation.DrawableRes
import androidx.appcompat.view.menu.ActionMenuItemView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.matcher.BoundedMatcher
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.hamcrest.Description
import org.hamcrest.Matcher

// https://stackoverflow.com/questions/34054009/how-to-test-actionmenuitemviews-icon-in-espresso
object Matchers {
    fun withActionIconDrawable(@DrawableRes resourceId: Int): Matcher<View?> {
        return object : BoundedMatcher<View?, ActionMenuItemView>(ActionMenuItemView::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("has image drawable resource $resourceId")
            }

            override fun matchesSafely(actionMenuItemView: ActionMenuItemView): Boolean {
                return sameBitmap(
                    actionMenuItemView.context,
                    actionMenuItemView.itemData.icon,
                    resourceId,
                    actionMenuItemView
                )
            }
        }
    }

    private fun sameBitmap(
        context: Context,
        drawable: Drawable?,
        resourceId: Int,
        view: View
    ): Boolean {
        var drawable = drawable
        val otherDrawable: Drawable? = context.resources.getDrawable(resourceId)
        if (drawable == null || otherDrawable == null) {
            return false
        }

        if (drawable is StateListDrawable) {
            val getStateDrawableIndex =
                StateListDrawable::class.java.getMethod(
                    "getStateDrawableIndex",
                    IntArray::class.java
                )
            val getStateDrawable =
                StateListDrawable::class.java.getMethod(
                    "getStateDrawable",
                    Int::class.javaPrimitiveType
                )
            val index = getStateDrawableIndex.invoke(drawable, view.drawableState)
            drawable = getStateDrawable.invoke(drawable, index) as Drawable
        }

        val bitmap = getBitmapFromDrawable(context, drawable)
        val otherBitmap = getBitmapFromDrawable(context, otherDrawable)
        return bitmap.sameAs(otherBitmap)
    }

    private fun getBitmapFromDrawable(context: Context?, drawable: Drawable): Bitmap {
        val bitmap: Bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}