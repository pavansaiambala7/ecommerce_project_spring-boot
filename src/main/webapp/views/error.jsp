<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!doctype html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<link rel="stylesheet"
	href="https://stackpath.bootstrapcdn.com/bootstrap/4.4.1/css/bootstrap.min.css"
	integrity="sha384-Vkoo8x4CGsO3+Hhxv8T/Q5PaXtkKtu6ug5TOeNV6gBiFeWPGFN9MuhOf23Q9Ifjh"
	crossorigin="anonymous">
<title>Error</title>
</head>
<body class="bg-light">

	<div class="container mt-5">
		<div class="card mx-auto" style="max-width: 40rem;">
			<div class="card-body text-center">
				<h1 class="card-title text-danger">
					<c:out value="${heading}" default="Something went wrong" />
				</h1>
				<p class="card-text lead">
					<c:out value="${detail}" default="Please try again." />
				</p>
				<a href="/" class="btn btn-primary mt-3">Back to the shop</a>
			</div>
		</div>
	</div>

</body>
</html>
