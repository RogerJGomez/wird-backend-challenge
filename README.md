## Setup

To run this project, install it locally using npm:

```
enviroment variables:

WEATHER_API_KEY=f0c9b61918cc460cbe211635242707
WEATHER_API_URL=https://api.weatherapi.com/v1/current.json

$connect to docker: docker run -d --name redis-stack-server -p 6379:6379 redis

$ cd ../project_folder
$ ./gradlew build
$ ./gradlew run
```
