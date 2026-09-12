<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!doctype html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<link rel="stylesheet"
	href="https://stackpath.bootstrapcdn.com/bootstrap/4.4.1/css/bootstrap.min.css"
	integrity="sha384-Vkoo8x4CGsO3+Hhxv8T/Q5PaXtkKtu6ug5TOeNV6gBiFeWPGFN9MuhOf23Q9Ifjh"
	crossorigin="anonymous">
<title>Your Cart</title>
</head>
<body class="bg-light">

	<nav class="navbar navbar-expand-lg navbar-dark bg-dark">
		<div class="container-fluid">
			<a class="navbar-brand" href="/">Perishable Shop</a>
			<ul class="navbar-nav ml-auto">
				<li class="nav-item"><a class="nav-link" href="/">Products</a></li>
				<li class="nav-item"><a class="nav-link" href="/profileDisplay">Profile</a></li>
				<li class="nav-item">
					<form action="/logout" method="post" class="form-inline">
						<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
						<button type="submit" class="btn btn-link nav-link">Logout</button>
					</form>
				</li>
			</ul>
		</div>
	</nav>

	<div class="container mt-4">
		<h2>Your Cart</h2>

		<c:choose>
			<c:when test="${empty items}">
				<div class="alert alert-info mt-3">
					Your cart is empty. <a href="/">Browse products</a>.
				</div>
			</c:when>
			<c:otherwise>
				<table class="table table-striped bg-white">
					<thead>
						<tr>
							<th scope="col">Product</th>
							<th scope="col">Unit price</th>
							<th scope="col">Quantity</th>
							<th scope="col">Line total</th>
							<th scope="col">Remove</th>
						</tr>
					</thead>
					<tbody>
						<c:forEach var="item" items="${items}">
							<tr>
								<td>
									<img src="<c:out value='${item.product.image}'/>" alt="" height="40" width="40"
										style="object-fit: contain;">
									<c:out value="${item.product.name}" />
								</td>
								<td><fmt:formatNumber value="${item.product.price}" type="currency" /></td>
								<td>
									<form action="/cart/update" method="post" class="form-inline">
										<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
										<input type="hidden" name="productId" value="${item.product.id}" />
										<input type="number" name="quantity" value="${item.quantity}" min="1"
											class="form-control form-control-sm mr-2" style="width: 5rem;" />
										<button type="submit" class="btn btn-sm btn-outline-primary">Update</button>
									</form>
								</td>
								<td><fmt:formatNumber value="${item.lineTotal}" type="currency" /></td>
								<td>
									<form action="/cart/remove" method="post">
										<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
										<input type="hidden" name="productId" value="${item.product.id}" />
										<button type="submit" class="btn btn-sm btn-danger">Remove</button>
									</form>
								</td>
							</tr>
						</c:forEach>
					</tbody>
				</table>

				<div class="d-flex justify-content-between align-items-center mt-3">
					<h4>
						Total: <fmt:formatNumber value="${total}" type="currency" />
					</h4>
					<form action="/cart/checkout" method="post">
						<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
						<button type="submit" class="btn btn-success btn-lg">Checkout</button>
					</form>
				</div>
			</c:otherwise>
		</c:choose>
	</div>

</body>
</html>
