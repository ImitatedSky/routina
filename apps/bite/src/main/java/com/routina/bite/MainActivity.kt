package com.routina.bite

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.routina.bite.data.defaultMeal
import com.routina.bite.model.Meal
import com.routina.bite.ui.AddFoodScreen
import com.routina.bite.ui.BiteViewModel
import com.routina.bite.ui.FoodEditScreen
import com.routina.bite.ui.FoodLibraryScreen
import com.routina.bite.ui.HistoryScreen
import com.routina.bite.ui.SettingsScreen
import com.routina.bite.ui.TodayScreen
import com.routina.bite.ui.WeightScreen
import com.routina.bite.ui.theme.BiteTheme

class MainActivity : ComponentActivity() {

    // 被能力跳板帶到前景時 +1，畫面看到值變了就回到今日頁
    private val openTodayRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BiteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BiteNavHost(openTodayRequests.intValue)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_TODAY, false)) {
            openTodayRequests.intValue += 1
        }
    }

    companion object {
        /** 跳板用來要求「回到今日頁」，一般啟動不會帶 */
        const val EXTRA_OPEN_TODAY = "com.routina.bite.extra.OPEN_TODAY"
    }
}

private object Routes {
    const val TODAY = "today"
    const val LIBRARY = "library"
    const val HISTORY = "history"
    const val WEIGHTS = "weights"
    const val SETTINGS = "settings"
    const val FOOD_NEW = "food/new"

    // add/{date}/{meal}：meal 帶 Meal 的名稱，例如 add/2025-09-18/LUNCH
    const val ADD = "add/{date}/{meal}"
    const val FOOD_EDIT = "food/{foodId}"

    const val ARG_DATE = "date"
    const val ARG_MEAL = "meal"
    const val ARG_FOOD_ID = "foodId"

    fun add(date: String, meal: Meal) = "add/$date/${meal.name}"
    fun food(foodId: String) = "food/$foodId"
}

@Composable
private fun BiteNavHost(openTodayRequests: Int) {
    val navController = rememberNavController()
    val viewModel: BiteViewModel = viewModel()

    // 被別的家族成員呼叫 open_today 時，把畫面帶回今日頁
    LaunchedEffect(openTodayRequests) {
        if (openTodayRequests > 0) {
            viewModel.backToToday()
            navController.popBackStack(Routes.TODAY, inclusive = false)
        }
    }

    NavHost(navController = navController, startDestination = Routes.TODAY) {

        composable(Routes.TODAY) {
            TodayScreen(
                viewModel = viewModel,
                onAdd = { date, meal -> navController.navigate(Routes.add(date, meal)) },
                onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenWeights = { navController.navigate(Routes.WEIGHTS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(
            route = Routes.ADD,
            arguments = listOf(
                navArgument(Routes.ARG_DATE) { type = NavType.StringType },
                navArgument(Routes.ARG_MEAL) { type = NavType.StringType }
            )
        ) { entry ->
            val date = entry.arguments?.getString(Routes.ARG_DATE).orEmpty()
            val meal = entry.arguments?.getString(Routes.ARG_MEAL)
                ?.let { name -> Meal.entries.firstOrNull { it.name == name } }
                ?: defaultMeal()
            AddFoodScreen(
                viewModel = viewModel,
                date = date,
                initialMeal = meal,
                onDone = { navController.popBackStack() },
                onEditFood = { foodId -> navController.navigate(Routes.food(foodId)) },
                onNewFood = { navController.navigate(Routes.FOOD_NEW) },
                onOpenLibrary = { navController.navigate(Routes.LIBRARY) }
            )
        }

        composable(Routes.LIBRARY) {
            FoodLibraryScreen(
                viewModel = viewModel,
                onEditFood = { foodId -> navController.navigate(Routes.food(foodId)) },
                onNewFood = { navController.navigate(Routes.FOOD_NEW) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.FOOD_NEW) {
            FoodEditScreen(
                viewModel = viewModel,
                foodId = null,
                onDone = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.FOOD_EDIT,
            arguments = listOf(navArgument(Routes.ARG_FOOD_ID) { type = NavType.StringType })
        ) { entry ->
            FoodEditScreen(
                viewModel = viewModel,
                foodId = entry.arguments?.getString(Routes.ARG_FOOD_ID),
                onDone = { navController.popBackStack() }
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                viewModel = viewModel,
                onOpenDate = { date ->
                    viewModel.selectDate(date)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.WEIGHTS) {
            WeightScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
