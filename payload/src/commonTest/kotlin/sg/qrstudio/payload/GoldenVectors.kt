package sg.qrstudio.payload

/**
 * BRD §10.2 / §10.3. Every string here was independently CRC-verified before use.
 *
 * Vectors A and B were produced by executing the reference implementation, so they
 * retain its D2 (unformatted amount) and D3 (point-of-initiation always "12")
 * behaviour. This port deliberately differs on exactly those two fields, so the tests
 * assert corrected expectations and keep the originals as structural and CRC references.
 */
object GoldenVectors {

    /** uen 201403121W, amount 500, editable, expiry 20201231, ref TQINV-10001. */
    const val VECTOR_A =
        "00020101021226490009SG.PAYNOW010120210201403121W030110408202012315204000053037025403500" +
            "5802SG5913ACME Pte Ltd.6009Singapore62150111TQINV-1000163044001"

    /**
     * uen 201403121W, no amount, no reference, expiry 20301231.
     *
     * The important CRC case: the raw checksum is 0xFC5 and must be emitted as "0FC5".
     * An implementation that skips the left-pad passes vector A and then fails on
     * roughly one code in sixteen.
     */
    const val VECTOR_B =
        "00020101021226490009SG.PAYNOW010120210201403121W03011040820301231520400005303702540105802SG" +
            "5913ACME Pte Ltd.6009Singapore63040FC5"

    /** Third-party payload (Go implementation), confirming tag 26 and reference placement. */
    const val VECTOR_C =
        "00020101021226470009SG.PAYNOW010120208123456780301004082026030452040000530370254040.99" +
            "5802SG5911testcompany6009Singapore62270123testordernumber1234567863040047"

    /**
     * §10.3 anti-vector. Well-formed EMVCo with a valid CRC, but PayNow sits at tag 36
     * with expiry at subtag 05 and the reference inside the template — the KTQRam layout
     * described in finding F2. PayNow detection must reject this.
     */
    const val ANTI_VECTOR =
        "00020101021136610009SG.PAYNOW010120210202300000A030100408ORDER789050820301231520400005303702" +
            "540512.005802SG5912ACME PTE LTD6009Singapore6304CA7D"
}
