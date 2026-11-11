#include <stdint.h>
#include <stdio.h>

int main(void) {
    const int64_t width = 800;
    const int64_t height = 600;
    const int64_t max_iter = 256;
    int64_t checksum = 0;

    for (int64_t y = 0; y < height; y++) {
        for (int64_t x = 0; x < width; x++) {
            const double cr = (double)x * 3.0 / (double)width - 2.0;
            const double ci = (double)y * 2.0 / (double)height - 1.0;
            double zr = 0.0;
            double zi = 0.0;
            int64_t iter = 0;

            for (int64_t k = 0; k < max_iter; k++) {
                if (zr * zr + zi * zi > 4.0) {
                    break;
                }

                const double next_r = zr * zr - zi * zi + cr;
                zi = 2.0 * zr * zi + ci;
                zr = next_r;
                iter = iter + 1;
            }

            checksum = checksum + iter;
        }
    }

    printf("%lld\n", (long long)checksum);
    return 0;
}
