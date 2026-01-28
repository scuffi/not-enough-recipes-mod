GRADLEW := ./gradlew
ifeq ($(OS),Windows_NT)
	GRADLEW := gradlew
endif

.PHONY: build clean run

build:
	$(GRADLEW) build

clean:
	$(GRADLEW) clean

run:
	$(GRADLEW) runClient
