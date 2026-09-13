<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!doctype html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta http-equiv="X-UA-Compatible" content="ie=edge">
<link rel="stylesheet"
	href="https://stackpath.bootstrapcdn.com/bootstrap/4.4.1/css/bootstrap.min.css"
	integrity="sha384-Vkoo8x4CGsO3+Hhxv8T/Q5PaXtkKtu6ug5TOeNV6gBiFeWPGFN9MuhOf23Q9Ifjh"
	crossorigin="anonymous">
<title>Products</title>
</head>
<body class="bg-light">
	<nav class="navbar navbar-expand-lg navbar-dark bg-dark">
		<div class="container-fluid">
			<a class="navbar-brand" href="/">Perishable Shop</a>
			<div class="collapse navbar-collapse">
				<ul class="navbar-nav ml-auto">
					<li class="nav-item"><a class="nav-link" href="/">Home</a></li>
					<li class="nav-item"><a class="nav-link" href="/cart">Cart</a></li>
					<li class="nav-item">
						<form action="/logout" method="post" class="form-inline">
							<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
							<button type="submit" class="btn btn-link nav-link">Logout</button>
						</form>
					</li>
				</ul>
			</div>
		</div>
	</nav>

	<div class="container-fluid mt-4">

		<c:if test="${not empty msg}">
			<div class="alert alert-info"><c:out value="${msg}" /></div>
		</c:if>

		<table class="table bg-white">
			<thead>
				<tr>
					<th scope="col">Id</th>
					<th scope="col">Product Name</th>
					<th scope="col">Category</th>
					<th scope="col">Preview</th>
					<th scope="col">Quantity</th>
					<th scope="col">Price</th>
					<th scope="col">Weight</th>
					<th scope="col">Description</th>
					<th scope="col">Buy</th>
				</tr>
			</thead>
			<tbody>
				<c:forEach var="product" items="${products}">
					<tr>
						<td>${product.id}</td>
						<td><c:out value="${product.name}" /></td>
						<td><c:out value="${product.category.name}" /></td>
						<td><img src="<c:out value='${product.image}'/>" height="100" width="100"
							style="object-fit: contain;" alt="<c:out value='${product.name}'/>"></td>
						<td>${product.quantity}</td>
						<td><fmt:formatNumber value="${product.price}" type="currency" /></td>
						<td>${product.weight}</td>
						<td><c:out value="${product.description}" /></td>
						<td>
							<c:choose>
								<c:when test="${product.quantity gt 0}">
									<%-- POST to the real cart endpoint. This form used to GET
									     products/addtocart, which no controller ever handled. --%>
									<form action="/cart/add" method="post">
										<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
										<input type="hidden" name="productId" value="${product.id}">
										<input type="hidden" name="quantity" value="1">
										<button type="submit" class="btn btn-warning">Add To Cart</button>
									</form>
								</c:when>
								<c:otherwise>
									<button class="btn btn-secondary" disabled>Out of stock</button>
								</c:otherwise>
							</c:choose>
						</td>
					</tr>
				</c:forEach>
			</tbody>
		</table>

	</div>
</body>
</html>
