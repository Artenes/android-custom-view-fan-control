package bok.artenes.customviewfromscratch

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

//this way of making the constructor is valid only when the style is irrelevant
class DialView @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null,
    style: Int = 0
) : View(context, attributes, style) {

    private var fanOffColor = Color.GRAY

    private var fanOnColor = Color.CYAN

    init {
        isClickable = true

        val typedArray = context.obtainStyledAttributes(attributes, R.styleable.DialView, 0, 0)
        fanOnColor = typedArray.getColor(R.styleable.DialView_fanOnColor, fanOnColor)
        fanOffColor = typedArray.getColor(R.styleable.DialView_fanOffColor, fanOffColor)
        typedArray.recycle()

    }

    private val textPaint = Paint().also {
        it.color = Color.BLACK
        it.style = Paint.Style.FILL_AND_STROKE
        it.textAlign = Paint.Align.CENTER
        it.textSize = 40f
    }

    private val dialPaint = Paint().also {
        it.color = fanOffColor
    }

    private var radius: Float = 0F

    private var activeSelection: Int = 0

    private val tempLabel = StringBuffer(8)

    private val tempResult = FloatArray(2)

    override fun onSizeChanged(width: Int, height: Int, oldw: Int, oldh: Int) {
        calculateRadius(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawDial(canvas)
        drawMarker(canvas)
        drawLabels(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (dialWasTouched(event)) {
            onDialTouched()
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun dialWasTouched(event: MotionEvent): Boolean {
        val wasTouched = event.action == MotionEvent.ACTION_UP
        val clickedX = event.x
        val clickedY = event.y
        val centerX = width / 2
        val centerY = height / 2

        //we use the distance between two points formula
        //to tell if the clicked point is inside or on the circle
        //https://math.stackexchange.com/questions/198764/how-to-know-if-a-point-is-inside-a-circle
        val distance = sqrt((clickedX - centerX).pow(2) + (clickedY - centerY).pow(2))

        return distance <= radius && wasTouched
    }

    private fun onDialTouched() {
        activeSelection = (activeSelection + 1) % SELECTION_COUNT
        if (activeSelection >= 1) {
            dialPaint.color = fanOnColor
        } else {
            dialPaint.color = fanOffColor
        }
        invalidate()
    }

    private fun drawDial(canvas: Canvas) {
        val xCenter = width / 2
        val yCenter = height / 2
        canvas.drawCircle(xCenter.toFloat(), yCenter.toFloat(), radius, dialPaint)
    }

    private fun drawMarker(canvas: Canvas) {
        //we want to put the marker a little inside the circle
        //not touching the outer circle border
        //so we take its radius and reduce it by an arbitrary amount
        val radiusWithPadding = this.radius - 35
        //arbitrary radius for the marker that we saw it looked fine
        val markerRadius = 20f
        val coordinates = calculateCoordinateForPosition(activeSelection, radiusWithPadding)
        val x = coordinates[0]
        val y = coordinates[1]
        canvas.drawCircle(x, y, markerRadius, textPaint)
    }

    private fun drawLabels(canvas: Canvas) {
        //we want to put the marker a little outside the circle
        //not touching the outer circle border
        //so we take its radius and add it by an arbitrary amount
        val radiusWithPadding = this.radius + 20
        val label = tempLabel
        for (position in 0 until SELECTION_COUNT) {
            val coordinate = calculateCoordinateForPosition(position, radiusWithPadding)
            val x = coordinate[0]
            val y = coordinate[1]
            label.setLength(0)
            label.append(position)
            canvas.drawText(label, 0, label.length, x, y, textPaint)
        }
    }

    private fun calculateRadius(width: Int, height: Int) {
        //the radius must be based on the smaller side of the view
        //if the radius would be of the bigger side, when draw, the circle
        //would go out of the bounds of the view, because the radius in one
        //of the sides would be greater.
        val smallerSide = min(width, height)

        //radius is the distance from the center of the view to its borders
        val radius = smallerSide / 2

        //we want a circle that is slightly smaller than the view size
        //so we take a percentage of the size of the radius
        val radiusWithPadding = radius * 0.8

        this.radius = radiusWithPadding.toFloat()
    }

    private fun calculateCoordinateForPosition(position: Int, radius: Float): FloatArray {
        val result = tempResult

        /**
         *
         * The problem:
         *
         * We have a circle.
         * We want to put a view inside/outside it based on its center.
         * Like if this view is attached to the center of the circle and
         * we can change its position by changing the angle it is in relation
         * to the center of the circle.
         *
         * The solution:
         *
         * Parametric Equation of a Circle (https://www.mathopenref.com/coordparamcircle.html)
         * In the link above there is a more clear explanation on how this works, but basically
         * we use pythagoras theorem for right triangles and the trigonometry functions sin and cos.
         *
         * The idea is that the radius is the hypotenuse of a right triangle. Its base
         * (which is the x coordinate) can be calculated using the cos function and its height
         * (which is the y coordinate) is calculated using the sin function.
         *
         * But the sin and cos functions only give us the ration of the size between the side of the
         * triangle and the hypotenuse. To actually get the size, we have to multiple the value
         * by the radius of the circle (that is the hypotenuse). So we end up with this formula:
         *
         * x = radius * cos(angle)
         * y = radius * sin(angle)
         *
         * BUT, this only works if the center of the circle is in the position 0,0. Since Android
         * points its 0,0 position at the top left corner of the screen, we have to adjust the formula
         * to compensate for this.
         *
         * x = radius * cos(angle) + (width / 2)
         * y = radius * sin(angle) + (height / 2)
         *
         * Where the width/2 and height/2 are the x and y centers of our circle in the Android coordinates system.
         */

        /**
         * We want to calculate a position in a circle in relation with its center.
         * For that we use angles (in radians).
         * In radians we use PI as the base for defining the angle.
         * Like in this image (http://cameronmitchellportfolio.weebly.com/uploads/1/8/3/4/18347141/3587536_orig.png)
         * we have to multiply PI by a fraction to actually move it around the 360 degrees of a circle.
         * In this case this fraction served well to put the view in the initial angle that we wanted.
         * This fraction can be any value that you want that puts the view where you need.
         * One of the numbers must have a fraction (.0) for the division not be considered an integer division
         * which would yield a wrong result. Int division = 1, float/double division = 1.125
         */
        val initialAngle = 9 / 8.0 * PI

        /**
         * Based on the position, we have to move from the initial angle.
         * The idea here is to get a fraction from the position and used it
         * to move the angle. We divide PI by 4 because we got only 4 options
         * and we multiply this result with PI to get this fraction value to add
         * to the initial angle.
         */
        val angle = initialAngle + position * (PI / 4)

        //the Parametric Equation explained above
        val x = (radius * cos(angle)) + (width / 2)

        //the Parametric Equation explained above
        val y = (radius * sin(angle)) + (height / 2)

        result[0] = x.toFloat()
        result[1] = y.toFloat()

        return result
    }

    companion object {

        private const val SELECTION_COUNT = 4

    }

}
