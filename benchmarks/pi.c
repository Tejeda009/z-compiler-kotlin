#include <stdint.h>
#include <stdio.h>

static double compute(int64_t iterations) {
    double pi = 0.0;
    double sign = 1.0;

    for (int64_t i = 0; i < iterations; i++) {
        pi = pi + (sign / (2 * i + 1));
        sign = -sign;
    }

    return pi * 4.0;
}

int main(void) {
    const int64_t passes = 100000000;
    const double result = compute(passes);

    printf("%.9f\n", result);
    return 0;
}
