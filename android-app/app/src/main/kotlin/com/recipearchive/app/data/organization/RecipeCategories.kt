package com.recipearchive.app.data.organization

object RecipeCategories {
    const val ENTREE = "Entrée"
    const val SIDE = "Side"
    const val APPETIZER = "Appetizer"
    const val SNACK = "Snack"
    const val DESSERT = "Dessert"
    const val BREAKFAST = "Breakfast"
    const val DRINK = "Drink"
    const val SAUCE = "Sauce"
    const val SOUP_SALAD = "Soup / Salad"
    const val OTHER = "Other"

    val all = listOf(ENTREE, SIDE, APPETIZER, SNACK, DESSERT, BREAKFAST, DRINK, SAUCE, SOUP_SALAD, OTHER)

    fun infer(title: String, ingredientText: String): String {
        inferFrom(title.lowercase())?.let { return it }
        return inferFrom("$title $ingredientText".lowercase()) ?: OTHER
    }

    private fun inferFrom(text: String): String? =
        when {
            text.containsAny("cocktail", "mocktail", "smoothie", "lemonade", "punch", "iced tea", "drink") -> DRINK
            text.containsAny("cake", "cookie", "pie", "tart", "brownie", "pudding", "frosting", "ice cream", "dessert", "cobbler", "cupcake", "candy", "truffle") -> DESSERT
            text.containsAny("pancake", "waffle", "omelet", "omelette", "scrambled egg", "breakfast", "french toast", "granola", "muffin", "dosa") -> BREAKFAST
            text.containsAny("soup", "stew", "chowder", "bisque", "salad") -> SOUP_SALAD
            text.containsAny("sauce", "dressing", "marinade", "salsa", "chutney", "gravy", "dip") -> SAUCE
            text.containsAny("appetizer", "bruschetta", "crostini", "deviled egg", "canapé", "canape") -> APPETIZER
            text.containsAny("snack", "popcorn", "trail mix", "cracker", "energy bite") -> SNACK
            text.containsAny("side dish", "potato", "potatoes", "vegetable", "vegetables", "coleslaw", "slaw", "rice pilaf", "bread", "biscuit", "rolls") -> SIDE
            text.containsAny("chicken", "beef", "pork", "salmon", "shrimp", "fish", "turkey", "lamb", "pasta", "lasagna", "casserole", "burger", "meatloaf", "parmesan", "eggplant parm") -> ENTREE
            else -> null
        }

    private fun String.containsAny(vararg candidates: String): Boolean = candidates.any { candidate ->
        Regex("\\b${Regex.escape(candidate)}\\b").containsMatchIn(this)
    }
}
