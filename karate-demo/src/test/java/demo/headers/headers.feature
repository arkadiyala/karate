Feature: multiple header management approaches

Background:

Given url demoBaseUrl
And path 'headers'
When method get
Then status 200

# even the responseCookies can be validated using 'match'
And match responseCookies contains { time: '#notnull' }

# example of how to check that a cookie does NOT exist
And match responseCookies !contains { blah: '#notnull' }

And def time = responseCookies.time.value
And def token = response

# note that the responseCookies will be auto-sent as cookies for all future requests

Scenario: configure function

    * configure headers = read('classpath:headers.js')

    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200

Scenario: configure json

    * configure headers = { Authorization: '#(token + time + demoBaseUrl)' }

    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200

Scenario: set header

    * header Authorization = token + time + demoBaseUrl

    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200

Scenario: multi-value headers

    * header Authorization = 'dummy', token + time + demoBaseUrl

    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200

Scenario: set headers using json

    * headers { Authorization: '#(token + time + demoBaseUrl)' }

    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200

Scenario: set multi-value headers using json

    * headers { Authorization: ['dummy', '#(token + time + demoBaseUrl)'] }

    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200

Scenario: set multi-value headers using function call

    # this is a test case for an edge case where commas in json confuse cucumber
    * def fun = function(arg){ return [arg.first, arg.second] }
    * header Authorization = call fun { first: 'dummy', second: '#(token + time + demoBaseUrl)' }

    Given path 'headers', token
    And param url = demoBaseUrl
    When method get
    Then status 200


