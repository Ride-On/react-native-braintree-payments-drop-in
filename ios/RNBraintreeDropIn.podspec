Pod::Spec.new do |s|
  s.name         = "RNBraintreeDropIn"
  s.version      = "1.3.1"
  s.summary      = "RNBraintreeDropIn"
  s.description  = <<-DESC
                  RNBraintreeDropIn
                   DESC
  s.homepage     = "https://github.com/beqaweb/react-native-braintree-payments-drop-in"
  s.license      = "MIT"
  # s.license      = { :type => "MIT", :file => "../LICENSE" }
  s.author             = { "author" => "lagrange.louis@gmail.com" }
  s.platform     = :ios, "12.0"
  s.source       = { :git => "https://github.com/Ride-On/react-native-braintree-payments-drop-in", :tag => "master" }
  s.source_files  = "*.{h,m}"
  s.requires_arc = true

  s.dependency "React"
  s.dependency "BraintreeDropIn", '~> 9.5.0'
  s.dependency "Braintree/DataCollector"
end