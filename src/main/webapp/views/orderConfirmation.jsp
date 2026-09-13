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
<title>Order Confirmed</title>
</head>
<body class="bg-light">

	<nav class="navbar navbar-expand-lg navbar-dark bg-dark">
		<div class="container-fluid">
			<a class="navbar-brand" href="/">Perishable Shop</a>
		</div>
	</nav>

	<div class="container mt-4">
		<div class="alert alert-success">
			<h4 class="alert-heading">Thank you, your order is placed.</h4>
			<p class="mb-0">
				Order <strong>#${order.id}</strong> &mdash; status
				<strong><c:out value="${order.status}" /></strong>
			</p>
		</div>

		<table class="table bg-white">
			<thead>
				<tr>
					<th scope="col">Product</th>
					<th scope="col">Quantity</th>
					<th scope="col">Unit price</th>
					<th scope="col">Line total</th>
				</tr>
			</thead>
			<tbody>
				<c:forEach var="item" items="${order.items}">
					<tr>
						<td><c:out value="${item.product.name}" /></td>
						<td>${item.quantity}</td>
						<td><fmt:formatNumber value="${item.price}" type="currency" /></td>
						<td><fmt:formatNumber value="${item.lineTotal}" type="currency" /></td>
					</tr>
				</c:forEach>
			</tbody>
		</table>

		<h4>
			Total: <fmt:formatNumber value="${order.totalAmount}" type="currency" />
		</h4>

		<a href="/" class="btn btn-primary mt-3">Continue shopping</a>
	</div>

</body>
</html>
