# BestGC++

## Description

BestGC++ is a profiling tool aiming to select the best Garbage Collector for a given application. It does so by benchmarking
your application, retrieving metrics used to score the Garbage Collectors with respect to execution time and GC pause time.
Although, previous Garbage Collection Scores are needed. Check the tool [BenchmarkGC](https://github.com/guilhas07/benchmark-gc) to compute them.

## Requirements

- Java Version: 21
- Maven

## How to run

After computing Garbage Collection Scores ( see [BenchmarkGC](https://github.com/guilhas07/benchmark-gc)) and having all the [Requirements](#requirements) you can run the web app like so:

1. Configure the the Garbage Collection matrix file in [src/main/resources/application.properties](src/main/resources/application.properties). The file should have a format
   like [example_matrix.json](example_matrix.json).

2. Run:

```
mvn clean package
java -jar target/bestGC-0.0.1-SNAPSHOT.jar
```

3. Go to `http://localhost:8000`

## Development

The development setups is simple:

1. Install JavaScript type dependencies with:

```
npm install
```

2. This project is configured with hot-reload, so any change to java files or template files will trigger a build. To run in
   dev mode:

```
mvn spring-boot:run
```

3. Go to `http://localhost:8000`

## Credits

BestGC++ is inspired by [BestGC](https://github.com/SaTaSo/BestGC-Software).
