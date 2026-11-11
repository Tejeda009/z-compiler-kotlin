#include <stdint.h>
#include <stdio.h>

int main(void) {
    const uint64_t iterations = 50000000;
    const uint64_t mask = 1000000007;
    uint64_t hash = 1469598103934665603;

    for (uint64_t i = 0; i < iterations; i++) {
        hash = (hash * 1099511628211 + i) % mask;
    }

    printf("%llu\n", (unsigned long long)hash);
    return 0;
}
