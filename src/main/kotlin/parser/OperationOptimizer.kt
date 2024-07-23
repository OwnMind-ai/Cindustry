package org.cindustry.parser

object OperationOptimizer {
    fun optimize(token: OperationToken): ExpressionToken {
        var result: ExpressionToken = token
        optimizeDuplicates(token) { result = it }
        optimizeChains(token, null) { result = it }

        return result
    }

    private fun optimizeChains(token: OperationToken, parent: OperationToken?, setter: (ExpressionToken) -> Unit) {
        val left = token.left
        val right = token.right

        if(optimizeChainPart(left, right, token.operator, parent, setter)){
            optimizeChains(left as OperationToken, parent, setter)
            return
        }

        if(optimizeChainPart(right, left, token.operator, parent, setter)){
            optimizeChains(right as OperationToken, parent, setter)
            return
        }

        if (left is OperationToken)
            optimizeChains(left, token) { token.left = it }

        if (right is OperationToken)
            optimizeChains(right, token) { token.right = it }
    }

    private fun optimizeChainPart(left: ExpressionToken, right: ExpressionToken, operator: OperatorToken, parent: OperationToken?, setter: (ExpressionToken) -> Unit): Boolean {
        if (left is OperationToken && right is NumberToken
            && left.operator.getPriority() == operator.getPriority() && operator.operator !in OperatorToken.LOGIC_OPERATION
        ) {
            val target: Pair<NumberToken, (ExpressionToken) -> Unit>? =
                if (left.right is NumberToken) Pair(left.right as NumberToken) { left.right = it }
                else if (left.left is NumberToken) Pair(left.left as NumberToken) { left.left = it }
                else null

            if (target != null) {
                var op = operator.operator
                if (left.operator.operator == "-")
                    op = if (op == "+") "-" else "+"

                val value = calculate(target.first.number.toDouble(), op, right.number.toDouble()) as NumberToken
                target.second.invoke(value)
                optimizeOperation(left, parent)
                setter.invoke(left)

                return true
            }
        }

        return false
    }

    private fun optimizeOperation(token: OperationToken, parent: OperationToken?) {
        val right = token.right
        val left = token.left

        if (right is NumberToken && right.number.toDouble() < 0){
            right.number = right.number.replace("-", "")
            token.operator.operator = if (token.operator.operator == "+") "-" else "+"
        } else if (left is NumberToken && left.number.toDouble() < 0){
            if (token.operator.operator == "+"){
                left.number = left.number.replace("-", "")
                token.right = left
                token.left = right

                token.operator.operator = "-"
            } else if (token.operator.operator == "-" && parent != null && parent.operator.operator in listOf("+", "-")){
                left.number = left.number.replace("-", "")
                token.operator.operator = "+"

                parent.operator.operator = if (parent.operator.operator == "+") "-" else "+"
            }
        }
    }

    private fun optimizeDuplicates(token: OperationToken, setter: (ExpressionToken) -> Unit): Boolean {
        val left = token.left
        val right = token.right
        val operator = token.operator.operator

        if (right is VariableToken && left is VariableToken && right.name.word == left.name.word){
            if (operator == "-"){
                setter.invoke(NumberToken("0"))
                return true
            } else if (operator == "/") {
                setter.invoke(NumberToken("1"))
                return true
            }
        } else if (right is NumberToken && left is NumberToken){
            setter.invoke(calculate(left.number.toDouble(), operator, right.number.toDouble()))
            return true
        } else if (right is BooleanToken && left is BooleanToken){
            setter.invoke(BooleanToken(calculateBool(left.value, operator, right.value)))
            return true
        } else if (right is NumberToken && right.number == "0" && operator in listOf("+", "-")) {
            setter.invoke(left)
            return true
        } else if (left is NumberToken && left.number == "0" && operator in listOf("+", "-")) {
            setter.invoke(right)
            return true
        }

        var changed = false
        if (left is OperationToken)
            changed = changed || optimizeDuplicates(left) { token.left = it }

        if (right is OperationToken)
            changed = changed || optimizeDuplicates(right) { token.right = it }

        if (changed)
            return optimizeDuplicates(token, setter)

        return false
    }

    private fun calculateBool(left: Boolean, operator: String, right: Boolean): Boolean {
        return when (operator){
            "==", "===" -> left == right
            "&&", "&" -> left && right
            "||", "|" -> left || right

            else -> throw IllegalArgumentException()
        }
    }

    private fun calculate(left: Double, operator: String, right: Double): ExpressionToken {
        return when (operator){
            "+" -> NumberToken((left + right).toOptimizedString())
            "-" -> NumberToken((left - right).toOptimizedString())
            "*" -> NumberToken((left * right).toOptimizedString())
            "/" -> NumberToken((left / right).toOptimizedString())
            "%" -> NumberToken((left % right).toOptimizedString())
            ">>" -> NumberToken((left.toLong() shr right.toInt()).toString())
            "<<" -> NumberToken((left.toLong() shl right.toInt()).toString())

            "==", "===" -> BooleanToken(left == right)
            ">" -> BooleanToken(left > right)
            ">=" -> BooleanToken(left >= right)
            "<" -> BooleanToken(left < right)
            "<=" -> BooleanToken(left <= right)

            else -> throw IllegalArgumentException()
        }
    }

    private fun Double.toOptimizedString(): String{
        if ( this.toInt().toDouble() == this) return this.toInt().toString()
        return this.toString()
    }
}