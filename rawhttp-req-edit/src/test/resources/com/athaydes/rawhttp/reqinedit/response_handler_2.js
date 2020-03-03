client.test("check", function() {
    client.assert(response.body == "foo bar");
});