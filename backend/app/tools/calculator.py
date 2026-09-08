import ast
import operator


_ALLOWED_OPERATORS = {
    ast.Add: operator.add,
    ast.Sub: operator.sub,
    ast.Mult: operator.mul,
    ast.Div: operator.truediv,
    ast.Mod: operator.mod,
    ast.Pow: operator.pow,
    ast.USub: operator.neg,
    ast.UAdd: operator.pos,
}


def calculate(expression: str):
    expression = (expression or "").strip()

    if not expression:
        raise ValueError("Expression is required.")

    if len(expression) > 200:
        raise ValueError("Expression is too long.")

    # Percentage expressions.
    import re

    percentage_of = re.fullmatch(
        r"(\d+(?:\.\d+)?)\s*(?:%|percent)\s+of\s+(\d+(?:\.\d+)?)",
        expression,
        re.IGNORECASE,
    )

    if percentage_of:
        percent = float(percentage_of.group(1))
        number = float(percentage_of.group(2))
        result = (percent / 100) * number
        return int(result) if result.is_integer() else result

    percentage_add = re.fullmatch(
        r"(\d+(?:\.\d+)?)\s*%\s*\+\s*(\d+(?:\.\d+)?)\s*%",
        expression,
    )

    if percentage_add:
        result = float(percentage_add.group(1)) + float(percentage_add.group(2))
        return int(result) if result.is_integer() else result

    tree = ast.parse(expression, mode="eval")

    def evaluate(node):
        if isinstance(node, ast.Expression):
            return evaluate(node.body)

        if isinstance(node, ast.Constant):
            if isinstance(node.value, (int, float)) and not isinstance(node.value, bool):
                return node.value
            raise ValueError("Only numbers are allowed.")

        if isinstance(node, ast.UnaryOp):
            operation = _ALLOWED_OPERATORS.get(type(node.op))
            if operation is None:
                raise ValueError("Operator is not allowed.")
            return operation(evaluate(node.operand))

        if isinstance(node, ast.BinOp):
            operation = _ALLOWED_OPERATORS.get(type(node.op))
            if operation is None:
                raise ValueError("Operator is not allowed.")

            left = evaluate(node.left)
            right = evaluate(node.right)

            if isinstance(node.op, ast.Pow) and abs(right) > 100:
                raise ValueError("Exponent is too large.")

            return operation(left, right)

        raise ValueError("Unsupported expression.")

    result = evaluate(tree)

    if isinstance(result, float) and result.is_integer():
        result = int(result)

    return result
